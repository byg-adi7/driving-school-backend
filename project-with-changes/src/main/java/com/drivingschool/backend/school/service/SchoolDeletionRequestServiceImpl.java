package com.drivingschool.backend.school.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.service.NotificationService;
import com.drivingschool.backend.school.dto.ReviewSchoolDeletionRequest;
import com.drivingschool.backend.school.dto.SchoolDeletionRequestResponse;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.entity.SchoolDeletionRequest;
import com.drivingschool.backend.school.enums.SchoolDeletionRequestStatus;
import com.drivingschool.backend.school.mapper.SchoolDeletionRequestMapper;
import com.drivingschool.backend.school.repository.SchoolDeletionRequestRepository;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SchoolDeletionRequestServiceImpl implements SchoolDeletionRequestService {

    private final SchoolDeletionRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final SchoolAdminCascadeDeletionService cascadeDeletionService;
    private final SchoolDeletionRequestMapper mapper;
    private final NotificationService notificationService;

    public SchoolDeletionRequestServiceImpl(SchoolDeletionRequestRepository requestRepository,
                                            UserRepository userRepository,
                                            SchoolRepository schoolRepository,
                                            SchoolAdminCascadeDeletionService cascadeDeletionService,
                                            SchoolDeletionRequestMapper mapper,
                                            NotificationService notificationService) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
        this.cascadeDeletionService = cascadeDeletionService;
        this.mapper = mapper;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public SchoolDeletionRequestResponse requestOwnSchoolDeletion(Long callerId) {
        User caller = userRepository.findById(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", callerId));

        if (caller.isBootstrapAdmin()) {
            throw new BadRequestException("The bootstrap admin does not own a school");
        }
        // Queried on School's owning FK side rather than User.ownedSchool - see
        // SchoolRepository.findByOwningAdminId for why the mappedBy side isn't used.
        School owned = schoolRepository.findByOwningAdminId(callerId).orElse(null);
        if (owned == null) {
            throw new BadRequestException("You do not own a school");
        }
        if (requestRepository.existsBySchoolIdAndStatus(owned.getId(), SchoolDeletionRequestStatus.PENDING)) {
            throw new BadRequestException("A deletion request for your school is already pending");
        }

        SchoolDeletionRequest request = SchoolDeletionRequest.builder()
                .school(owned)
                .schoolName(owned.getName())
                .status(SchoolDeletionRequestStatus.PENDING)
                .requestedBy(caller)
                .requestedByEmail(caller.getEmail())
                .build();
        SchoolDeletionRequest saved = requestRepository.save(request);

        notifyBootstrapAdmin(saved);
        log.info("Admin {} requested deletion of school id={}", caller.getEmail(), owned.getId());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SchoolDeletionRequestResponse> listPending(Pageable pageable, Long callerId) {
        requireBootstrap(callerId);
        return requestRepository.findByStatus(SchoolDeletionRequestStatus.PENDING, pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional
    public SchoolDeletionRequestResponse approve(Long requestId, Long callerId, ReviewSchoolDeletionRequest body) {
        User caller = requireBootstrap(callerId);
        SchoolDeletionRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolDeletionRequest", "id", requestId));
        if (request.getStatus() != SchoolDeletionRequestStatus.PENDING) {
            throw new BadRequestException("This request has already been reviewed");
        }
        School school = request.getSchool();
        if (school == null) {
            throw new BadRequestException("The school for this request no longer exists");
        }
        Long schoolId = school.getId();

        request.approve(caller, body != null ? body.getReviewNotes() : null);
        // saveAndFlush, not save: the cascade below deletes the school/admin via a
        // bulk delete that executes immediately (bypassing Hibernate's deferred-write
        // persistence context entirely), including the DB's ON DELETE SET NULL on this
        // very row's school_id. If this UPDATE were left pending instead, it would only
        // flush later (at the transaction's natural end) and try to re-write the STALE
        // pre-cascade school_id value, failing the FK constraint against a school that
        // by then no longer exists. Flushing first ensures this row's own write lands
        // while the school still exists, before the cascade's SET NULL takes over.
        requestRepository.saveAndFlush(request);
        // Snapshot the response BEFORE the cascade runs: the cascade deletes the
        // admin's User row, which - via ON DELETE SET NULL - nulls this very
        // request's school/requestedBy columns at the DB level without
        // Hibernate's persistence context knowing, so building the response
        // afterward risks a stale read.
        SchoolDeletionRequestResponse response = mapper.toResponse(request);

        cascadeDeletionService.execute(schoolId);

        log.info("Bootstrap admin {} approved school deletion request id={}", caller.getEmail(), requestId);
        return response;
    }

    @Override
    @Transactional
    public SchoolDeletionRequestResponse reject(Long requestId, Long callerId, ReviewSchoolDeletionRequest body) {
        User caller = requireBootstrap(callerId);
        SchoolDeletionRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolDeletionRequest", "id", requestId));
        if (request.getStatus() != SchoolDeletionRequestStatus.PENDING) {
            throw new BadRequestException("This request has already been reviewed");
        }
        request.reject(caller, body != null ? body.getReviewNotes() : null);
        SchoolDeletionRequest saved = requestRepository.save(request);
        log.info("Bootstrap admin {} rejected school deletion request id={}", caller.getEmail(), requestId);
        notifyRequesterOfRejection(saved);
        return mapper.toResponse(saved);
    }

    private User requireBootstrap(Long callerId) {
        User caller = userRepository.findById(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", callerId));
        if (!caller.isBootstrapAdmin()) {
            throw new BadRequestException("Only the bootstrap admin can review school deletion requests");
        }
        return caller;
    }

    private void notifyBootstrapAdmin(SchoolDeletionRequest request) {
        userRepository.findByBootstrapAdminTrue().ifPresent(bootstrap -> {
            try {
                notificationService.send(SendNotificationRequest.builder()
                        .userId(bootstrap.getId())
                        .subject("Action required: school deletion request awaiting your approval")
                        .body(("Admin %s has requested to permanently delete their school \"%s\" (school ID %d). " +
                                "Approving this request will delete the school and the requesting admin's account " +
                                "together, along with all of that school's students, instructors, bookings, and " +
                                "other records. Please review it in the School Deletion Requests queue before approving.")
                                .formatted(request.getRequestedByEmail(), request.getSchoolName(), request.getSchool().getId()))
                        .channel(NotificationChannel.EMAIL)
                        .build());
            } catch (Exception ex) {
                log.warn("Failed to send school deletion request EMAIL notification: requestId={}", request.getId(), ex);
            }
            try {
                notificationService.send(SendNotificationRequest.builder()
                        .userId(bootstrap.getId())
                        .subject("School deletion request pending")
                        .body(("%s has requested deletion of their school \"%s\". This is only a request - " +
                                "nothing has been deleted yet. Review and approve or reject it from the School " +
                                "Deletion Requests queue.")
                                .formatted(request.getRequestedByEmail(), request.getSchoolName()))
                        .channel(NotificationChannel.IN_APP)
                        .build());
            } catch (Exception ex) {
                log.warn("Failed to send school deletion request IN_APP notification: requestId={}", request.getId(), ex);
            }
        });
    }

    // requestedBy is nullable (ON DELETE SET NULL) - guard against the requester's
    // account having been removed some other way between request and review.
    private void notifyRequesterOfRejection(SchoolDeletionRequest request) {
        User requester = request.getRequestedBy();
        if (requester == null) {
            return;
        }
        String reviewNotes = request.getReviewNotes();
        String body = reviewNotes != null && !reviewNotes.isBlank()
                ? "Your request to delete school \"%s\" was rejected. Reviewer notes: %s".formatted(request.getSchoolName(), reviewNotes)
                : "Your request to delete school \"%s\" was rejected.".formatted(request.getSchoolName());
        try {
            notificationService.send(SendNotificationRequest.builder()
                    .userId(requester.getId())
                    .subject("School deletion request rejected")
                    .body(body)
                    .channel(NotificationChannel.IN_APP)
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to send deletion-rejected IN_APP notification: requestId={}", request.getId(), ex);
        }
        try {
            notificationService.send(SendNotificationRequest.builder()
                    .userId(requester.getId())
                    .subject("School deletion request rejected")
                    .body(body)
                    .channel(NotificationChannel.EMAIL)
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to send deletion-rejected EMAIL notification: requestId={}", request.getId(), ex);
        }
    }
}
