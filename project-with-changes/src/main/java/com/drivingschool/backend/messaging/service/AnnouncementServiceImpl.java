package com.drivingschool.backend.messaging.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.messaging.dto.AnnouncementResponse;
import com.drivingschool.backend.messaging.dto.CreateAnnouncementRequest;
import com.drivingschool.backend.messaging.entity.Announcement;
import com.drivingschool.backend.messaging.repository.AnnouncementRepository;
import com.drivingschool.backend.notification.entity.Notification;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.enums.NotificationStatus;
import com.drivingschool.backend.notification.mapper.NotificationMapper;
import com.drivingschool.backend.notification.repository.NotificationRepository;
import com.drivingschool.backend.realtime.RealtimeEvent;
import com.drivingschool.backend.realtime.RealtimePublisher;
import com.drivingschool.backend.school.validator.CallerSchoolScope;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * One-way announcements from an instructor to every student of their school,
 * delivered in-app and by email. Students can't reply to an announcement - they
 * message the instructor directly, which keeps replies private.
 */
@Slf4j
@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final NotificationRepository notificationRepository;
    private final AnnouncementEmailDispatcher emailDispatcher;
    private final CurrentUserService currentUserService;
    private final CallerSchoolScope callerSchoolScope;
    private final RealtimePublisher realtimePublisher;
    private final NotificationMapper notificationMapper;

    public AnnouncementServiceImpl(AnnouncementRepository announcementRepository,
                                   InstructorProfileRepository instructorProfileRepository,
                                   StudentProfileRepository studentProfileRepository,
                                   NotificationRepository notificationRepository,
                                   AnnouncementEmailDispatcher emailDispatcher,
                                   CurrentUserService currentUserService,
                                   CallerSchoolScope callerSchoolScope,
                                   RealtimePublisher realtimePublisher,
                                   NotificationMapper notificationMapper) {
        this.announcementRepository = announcementRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.notificationRepository = notificationRepository;
        this.emailDispatcher = emailDispatcher;
        this.currentUserService = currentUserService;
        this.callerSchoolScope = callerSchoolScope;
        this.realtimePublisher = realtimePublisher;
        this.notificationMapper = notificationMapper;
    }

    @Override
    @Transactional
    public AnnouncementResponse create(CreateAnnouncementRequest request) {
        Long userId = currentUserService.requireUserId();
        InstructorProfile instructor = instructorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor profile not found for user ID: " + userId));

        Announcement announcement = announcementRepository.save(Announcement.builder()
                .school(instructor.getSchool())
                .instructor(instructor)
                .subject(request.getSubject())
                .body(request.getBody())
                .build());

        List<StudentProfile> students = studentProfileRepository
                .findBySchoolIdExcludingDeletedUsers(instructor.getSchool().getId());
        String subject = "Announcement from %s %s: %s".formatted(
                instructor.getFirstName(), instructor.getLastName(), request.getSubject());

        // Saved directly rather than through NotificationService.send() once per student:
        // an in-app notification is just a stored SENT row, and email goes out in Resend
        // batches (AnnouncementEmailDispatcher) instead of one request per student.
        List<Notification> inApp = new ArrayList<>();
        List<Notification> email = new ArrayList<>();
        for (StudentProfile student : students) {
            Notification inAppNotification = Notification.builder()
                    .user(student.getUser())
                    .subject(subject)
                    .body(request.getBody())
                    .channel(NotificationChannel.IN_APP)
                    .status(NotificationStatus.PENDING)
                    .recipientAddress(student.getUser().getEmail())
                    .build();
            inAppNotification.markSent();
            inApp.add(inAppNotification);
            email.add(Notification.builder()
                    .user(student.getUser())
                    .subject(subject)
                    .body(request.getBody())
                    .channel(NotificationChannel.EMAIL)
                    .status(NotificationStatus.PENDING)
                    .recipientAddress(student.getUser().getEmail())
                    .build());
        }
        AnnouncementResponse response = toResponse(announcement, students.size());
        for (Notification notification : notificationRepository.saveAll(inApp)) {
            Long studentUserId = notification.getUser().getId();
            realtimePublisher.publishAfterCommit(studentUserId, RealtimeEvent.NOTIFICATION_CREATED,
                    notificationMapper.toResponse(notification));
            realtimePublisher.publishAfterCommit(studentUserId, RealtimeEvent.ANNOUNCEMENT_CREATED, response);
        }
        List<Long> emailIds = notificationRepository.saveAll(email).stream().map(Notification::getId).toList();
        dispatchEmailsAfterCommit(emailIds);

        log.info("Instructor {} announced to {} students of school {}", instructor.getId(), students.size(),
                instructor.getSchool().getId());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AnnouncementResponse> listForMySchool(Pageable pageable) {
        return callerSchoolScope.callerSchoolId()
                .map(schoolId -> announcementRepository.findBySchoolId(schoolId, pageable))
                .orElseGet(() -> announcementRepository.findAllNewestFirst(pageable))
                .map(a -> toResponse(a, null));
    }

    // Same reasoning as NotificationServiceImpl.dispatch: the async dispatcher's thread
    // can't see these rows until this transaction commits, and a rolled-back
    // announcement must never be emailed.
    private void dispatchEmailsAfterCommit(List<Long> emailIds) {
        if (emailIds.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emailDispatcher.dispatch(emailIds);
                }
            });
            return;
        }
        emailDispatcher.dispatch(emailIds);
    }

    private AnnouncementResponse toResponse(Announcement a, Integer recipientCount) {
        return AnnouncementResponse.builder()
                .id(a.getId())
                .schoolId(a.getSchool().getId())
                .instructorProfileId(a.getInstructor().getId())
                .instructorName(a.getInstructor().getFirstName() + " " + a.getInstructor().getLastName())
                .subject(a.getSubject())
                .body(a.getBody())
                .createdAt(a.getCreatedAt())
                .recipientCount(recipientCount)
                .build();
    }
}
