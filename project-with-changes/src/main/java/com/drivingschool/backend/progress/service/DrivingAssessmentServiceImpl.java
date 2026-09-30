package com.drivingschool.backend.progress.service;

import com.drivingschool.backend.booking.entity.Booking;
import com.drivingschool.backend.booking.repository.BookingRepository;
import com.drivingschool.backend.common.transaction.AfterCommit;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.gamification.service.GamificationService;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.service.NotificationService;
import com.drivingschool.backend.progress.dto.CreateDrivingAssessmentRequest;
import com.drivingschool.backend.progress.dto.DrivingAssessmentResponse;
import com.drivingschool.backend.progress.dto.UpdateDrivingAssessmentFeedbackRequest;
import com.drivingschool.backend.progress.entity.DrivingAssessment;
import com.drivingschool.backend.progress.enums.AssessmentResult;
import com.drivingschool.backend.progress.mapper.DrivingAssessmentMapper;
import com.drivingschool.backend.progress.repository.DrivingAssessmentRepository;
import com.drivingschool.backend.progress.validator.DrivingAssessmentValidator;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class DrivingAssessmentServiceImpl implements DrivingAssessmentService {

    private final DrivingAssessmentRepository drivingAssessmentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final BookingRepository bookingRepository;
    private final DrivingAssessmentMapper mapper;
    private final DrivingAssessmentValidator validator;
    private final NotificationService notificationService;
    private final GamificationService gamificationService;

    public DrivingAssessmentServiceImpl(DrivingAssessmentRepository drivingAssessmentRepository,
                                        StudentProfileRepository studentProfileRepository,
                                        InstructorProfileRepository instructorProfileRepository,
                                        BookingRepository bookingRepository,
                                        DrivingAssessmentMapper mapper,
                                        DrivingAssessmentValidator validator,
                                        NotificationService notificationService,
                                        GamificationService gamificationService) {
        this.drivingAssessmentRepository = drivingAssessmentRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.bookingRepository = bookingRepository;
        this.mapper = mapper;
        this.validator = validator;
        this.notificationService = notificationService;
        this.gamificationService = gamificationService;
    }

    @Override
    @Transactional
    public DrivingAssessmentResponse createAssessment(CreateDrivingAssessmentRequest request, Long callerId) {
        validator.validateCreateRequest(request);

        InstructorProfile instructor = instructorProfileRepository.findByUserId(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor profile not found for user ID: " + callerId));

        StudentProfile student = studentProfileRepository.findById(request.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("StudentProfile", "id", request.getStudentId()));
        // Same reasoning as lesson notes: recording an assessment grants read access to the
        // student's assessment history (and a PASSED result awards them points).
        if (!student.getSchool().getId().equals(instructor.getSchool().getId())) {
            throw new ForbiddenException("You can only assess students in your own school");
        }

        Booking booking = null;
        if (request.getBookingId() != null) {
            booking = bookingRepository.findById(request.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", request.getBookingId()));
            if (!booking.getInstructor().getId().equals(instructor.getId())
                    || !booking.getStudent().getId().equals(student.getId())) {
                throw new BadRequestException("Booking does not belong to this instructor and student");
            }
        }

        DrivingAssessment assessment = DrivingAssessment.builder()
                .student(student)
                .instructor(instructor)
                .booking(booking)
                .assessmentDate(request.getAssessmentDate())
                .score(request.getScore())
                .result(request.getResult())
                .feedback(request.getFeedback())
                .durationMinutes(request.getDurationMinutes())
                .build();

        DrivingAssessment saved = drivingAssessmentRepository.save(assessment);
        log.info("Driving assessment recorded: id={}, studentId={}, instructorId={}, result={}",
                saved.getId(), student.getId(), instructor.getId(), saved.getResult());

        notifyStudentOfAssessment(saved);
        if (saved.getResult() == AssessmentResult.PASSED) {
            awardGamificationPoints(saved);
        }

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DrivingAssessmentResponse updateFeedback(Long assessmentId, UpdateDrivingAssessmentFeedbackRequest request, Long callerId) {
        DrivingAssessment assessment = findById(assessmentId);
        validator.validateOwnership(assessment, callerId);
        assessment.updateFeedback(request.getFeedback());
        return mapper.toResponse(drivingAssessmentRepository.save(assessment));
    }

    @Override
    @Transactional(readOnly = true)
    public DrivingAssessmentResponse getAssessment(Long assessmentId, Long userId, String role) {
        DrivingAssessment assessment = findById(assessmentId);
        validator.validateReadAccess(assessment, userId, role);
        return mapper.toResponse(assessment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DrivingAssessmentResponse> getStudentAssessments(Long studentId, Long currentUserId, String role) {
        StudentProfile student = studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentProfile", "id", studentId));

        boolean hasTaughtStudent = "INSTRUCTOR".equals(role) && instructorProfileRepository.findByUserId(currentUserId)
                .map(instructor -> drivingAssessmentRepository.existsByStudent_IdAndInstructor_Id(student.getId(), instructor.getId()))
                .orElse(false);
        validator.validateStudentAssessmentsAccess(student, currentUserId, role, hasTaughtStudent);

        return drivingAssessmentRepository.findByStudentIdOrderByAssessmentDateDesc(student.getId()).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DrivingAssessmentResponse> getInstructorAssessments(Long instructorId, Long currentUserId, String role) {
        InstructorProfile instructor = instructorProfileRepository.findById(instructorId)
                .orElseThrow(() -> new ResourceNotFoundException("InstructorProfile", "id", instructorId));
        validator.validateInstructorAssessmentsAccess(instructor, currentUserId, role);

        return drivingAssessmentRepository.findByInstructorIdOrderByAssessmentDateDesc(instructor.getId()).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private DrivingAssessment findById(Long assessmentId) {
        return drivingAssessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("DrivingAssessment", "id", assessmentId));
    }

    private void notifyStudentOfAssessment(DrivingAssessment a) {
        try {
            String instructorName = a.getInstructor().getFirstName() + " " + a.getInstructor().getLastName();
            String body = switch (a.getResult()) {
                case PASSED -> "Great news! You passed your driving assessment with %s, scoring %d/100."
                        .formatted(instructorName, a.getScore());
                case FAILED -> "Your driving assessment with %s resulted in a score of %d/100 (not passing). Review the feedback and keep practicing."
                        .formatted(instructorName, a.getScore());
                case NEEDS_IMPROVEMENT -> "Your driving assessment with %s scored %d/100 - needs improvement. Check the feedback from your instructor."
                        .formatted(instructorName, a.getScore());
                case PENDING -> "Your driving assessment has been recorded and is pending review.";
            };
            SendNotificationRequest request = SendNotificationRequest.builder()
                    .userId(a.getStudent().getUser().getId())
                    .subject("Driving assessment result: " + a.getResult())
                    .body(body)
                    .channel(NotificationChannel.IN_APP)
                    .build();
            notificationService.sendAfterCommit(request);
        } catch (Exception ex) {
            log.warn("Failed to send driving assessment notification: assessmentId={}", a.getId(), ex);
        }
    }

    // After commit: points only for an assessment that was actually saved.
    private void awardGamificationPoints(DrivingAssessment a) {
        Long studentId = a.getStudent().getId();
        Long assessmentId = a.getId();
        AfterCommit.run("award points for assessment " + assessmentId,
                () -> gamificationService.awardAssessmentPassed(studentId, assessmentId));
    }
}
