package com.drivingschool.backend.progress.service;

import com.drivingschool.backend.booking.entity.Booking;
import com.drivingschool.backend.booking.enums.BookingStatus;
import com.drivingschool.backend.booking.enums.BookingType;
import com.drivingschool.backend.booking.repository.BookingRepository;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.gamification.service.GamificationService;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.notification.service.NotificationService;
import com.drivingschool.backend.progress.dto.CreateDrivingAssessmentRequest;
import com.drivingschool.backend.progress.dto.UpdateDrivingAssessmentFeedbackRequest;
import com.drivingschool.backend.progress.entity.DrivingAssessment;
import com.drivingschool.backend.progress.enums.AssessmentResult;
import com.drivingschool.backend.progress.mapper.DrivingAssessmentMapper;
import com.drivingschool.backend.progress.repository.DrivingAssessmentRepository;
import com.drivingschool.backend.progress.validator.DrivingAssessmentValidator;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrivingAssessmentServiceImplTest {

    @Mock private DrivingAssessmentRepository drivingAssessmentRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private NotificationService notificationService;
    @Mock private GamificationService gamificationService;
    private final DrivingAssessmentMapper mapper = new DrivingAssessmentMapper();
    private final DrivingAssessmentValidator validator = new DrivingAssessmentValidator();

    private DrivingAssessmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DrivingAssessmentServiceImpl(drivingAssessmentRepository, studentProfileRepository,
                instructorProfileRepository, bookingRepository, mapper, validator, notificationService,
                gamificationService);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private StudentProfile studentProfile(Long profileId, User user) {
        StudentProfile student = StudentProfile.builder().firstName("Sam").lastName("Student")
                .user(user).school(School.builder().active(true).build()).build();
        ReflectionTestUtils.setField(student, "id", profileId);
        return student;
    }

    private InstructorProfile instructorProfile(Long profileId, User user) {
        InstructorProfile instructor = InstructorProfile.builder().firstName("Ivy").lastName("Instructor")
                .user(user).active(true).school(School.builder().active(true).build()).build();
        ReflectionTestUtils.setField(instructor, "id", profileId);
        return instructor;
    }

    private CreateDrivingAssessmentRequest validRequest(AssessmentResult result) {
        CreateDrivingAssessmentRequest request = new CreateDrivingAssessmentRequest();
        request.setStudentId(60L);
        request.setAssessmentDate(LocalDateTime.now());
        request.setScore(85);
        request.setResult(result);
        request.setFeedback("Solid drive.");
        return request;
    }

    // --- createAssessment ---

    @Test
    void createAssessment_validRequest_savesAndReturnsResponse() {
        CreateDrivingAssessmentRequest request = validRequest(AssessmentResult.PASSED);
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        StudentProfile student = studentProfile(60L, userWithId(2L));

        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        when(studentProfileRepository.findById(60L)).thenReturn(Optional.of(student));
        when(drivingAssessmentRepository.save(any(DrivingAssessment.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.createAssessment(request, 1L);

        assertThat(response.getStudentId()).isEqualTo(60L);
        assertThat(response.getInstructorId()).isEqualTo(50L);
        assertThat(response.getResult()).isEqualTo(AssessmentResult.PASSED);
    }

    @Test
    void createAssessment_whenInstructorProfileMissing_throwsResourceNotFoundException() {
        CreateDrivingAssessmentRequest request = validRequest(AssessmentResult.PASSED);
        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAssessment(request, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createAssessment_withBookingBelongingToDifferentInstructor_throwsBadRequestException() {
        CreateDrivingAssessmentRequest request = validRequest(AssessmentResult.PASSED);
        request.setBookingId(500L);

        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        InstructorProfile otherInstructor = instructorProfile(51L, userWithId(9L));
        StudentProfile student = studentProfile(60L, userWithId(2L));
        Booking booking = Booking.builder().student(student).instructor(otherInstructor)
                .school(School.builder().active(true).build())
                .scheduledAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(1).plusHours(1))
                .durationMinutes(60)
                .status(BookingStatus.CONFIRMED)
                .bookingType(BookingType.ROAD_LESSON)
                .build();
        ReflectionTestUtils.setField(booking, "id", 500L);

        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        when(studentProfileRepository.findById(60L)).thenReturn(Optional.of(student));
        when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.createAssessment(request, 1L))
                .isInstanceOf(BadRequestException.class);

        verify(drivingAssessmentRepository, never()).save(any());
    }

    @Test
    void createAssessment_onSuccess_sendsInAppNotification() {
        CreateDrivingAssessmentRequest request = validRequest(AssessmentResult.PASSED);
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        StudentProfile student = studentProfile(60L, userWithId(2L));

        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        when(studentProfileRepository.findById(60L)).thenReturn(Optional.of(student));
        when(drivingAssessmentRepository.save(any(DrivingAssessment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createAssessment(request, 1L);

        verify(notificationService).send(any());
    }

    @Test
    void createAssessment_whenNotificationThrows_assessmentStillSucceeds() {
        CreateDrivingAssessmentRequest request = validRequest(AssessmentResult.PASSED);
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        StudentProfile student = studentProfile(60L, userWithId(2L));

        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        when(studentProfileRepository.findById(60L)).thenReturn(Optional.of(student));
        when(drivingAssessmentRepository.save(any(DrivingAssessment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificationService.send(any())).thenThrow(new RuntimeException("notification service down"));

        assertThatCode(() -> service.createAssessment(request, 1L)).doesNotThrowAnyException();
    }

    @Test
    void createAssessment_whenPassed_awardsGamificationPoints() {
        CreateDrivingAssessmentRequest request = validRequest(AssessmentResult.PASSED);
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        StudentProfile student = studentProfile(60L, userWithId(2L));

        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        when(studentProfileRepository.findById(60L)).thenReturn(Optional.of(student));
        when(drivingAssessmentRepository.save(any(DrivingAssessment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createAssessment(request, 1L);

        verify(gamificationService).awardAssessmentPassed(60L, null);
    }

    @Test
    void createAssessment_whenNotPassed_doesNotAwardGamificationPoints() {
        CreateDrivingAssessmentRequest request = validRequest(AssessmentResult.FAILED);
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        StudentProfile student = studentProfile(60L, userWithId(2L));

        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        when(studentProfileRepository.findById(60L)).thenReturn(Optional.of(student));
        when(drivingAssessmentRepository.save(any(DrivingAssessment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createAssessment(request, 1L);

        verify(gamificationService, never()).awardAssessmentPassed(any(), any());
    }

    @Test
    void createAssessment_whenGamificationThrows_assessmentStillSucceeds() {
        CreateDrivingAssessmentRequest request = validRequest(AssessmentResult.PASSED);
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        StudentProfile student = studentProfile(60L, userWithId(2L));

        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(instructor));
        when(studentProfileRepository.findById(60L)).thenReturn(Optional.of(student));
        when(drivingAssessmentRepository.save(any(DrivingAssessment.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("gamification down"))
                .when(gamificationService).awardAssessmentPassed(any(), any());

        assertThatCode(() -> service.createAssessment(request, 1L)).doesNotThrowAnyException();
    }

    // --- updateFeedback ---

    @Test
    void updateFeedback_byNonAuthoringInstructor_throwsBadRequestException() {
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        StudentProfile student = studentProfile(60L, userWithId(2L));
        DrivingAssessment assessment = DrivingAssessment.builder()
                .student(student).instructor(instructor)
                .assessmentDate(LocalDateTime.now()).score(70).result(AssessmentResult.PASSED)
                .build();
        ReflectionTestUtils.setField(assessment, "id", 900L);

        when(drivingAssessmentRepository.findById(900L)).thenReturn(Optional.of(assessment));

        UpdateDrivingAssessmentFeedbackRequest request = new UpdateDrivingAssessmentFeedbackRequest();
        request.setFeedback("Edited");

        assertThatThrownBy(() -> service.updateFeedback(900L, request, 999L))
                .isInstanceOf(BadRequestException.class);

        verify(drivingAssessmentRepository, never()).save(any());
    }

    @Test
    void updateFeedback_byAuthoringInstructor_updatesFeedback() {
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        StudentProfile student = studentProfile(60L, userWithId(2L));
        DrivingAssessment assessment = DrivingAssessment.builder()
                .student(student).instructor(instructor)
                .assessmentDate(LocalDateTime.now()).score(70).result(AssessmentResult.PASSED)
                .build();
        ReflectionTestUtils.setField(assessment, "id", 900L);

        when(drivingAssessmentRepository.findById(900L)).thenReturn(Optional.of(assessment));
        when(drivingAssessmentRepository.save(any(DrivingAssessment.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateDrivingAssessmentFeedbackRequest request = new UpdateDrivingAssessmentFeedbackRequest();
        request.setFeedback("Edited feedback");

        var response = service.updateFeedback(900L, request, 1L);

        assertThat(response.getFeedback()).isEqualTo("Edited feedback");
    }

    // --- getAssessment ---

    @Test
    void getAssessment_studentAccessingOthersAssessment_throwsBadRequestException() {
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        StudentProfile student = studentProfile(60L, userWithId(2L));
        DrivingAssessment assessment = DrivingAssessment.builder()
                .student(student).instructor(instructor)
                .assessmentDate(LocalDateTime.now()).score(70).result(AssessmentResult.PASSED)
                .build();
        ReflectionTestUtils.setField(assessment, "id", 900L);

        when(drivingAssessmentRepository.findById(900L)).thenReturn(Optional.of(assessment));

        assertThatThrownBy(() -> service.getAssessment(900L, 999L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getAssessment_asOwningStudent_returnsResponse() {
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L));
        StudentProfile student = studentProfile(60L, userWithId(2L));
        DrivingAssessment assessment = DrivingAssessment.builder()
                .student(student).instructor(instructor)
                .assessmentDate(LocalDateTime.now()).score(70).result(AssessmentResult.PASSED)
                .build();
        ReflectionTestUtils.setField(assessment, "id", 900L);

        when(drivingAssessmentRepository.findById(900L)).thenReturn(Optional.of(assessment));

        var response = service.getAssessment(900L, 2L, "STUDENT");

        assertThat(response.getId()).isEqualTo(900L);
    }
}
