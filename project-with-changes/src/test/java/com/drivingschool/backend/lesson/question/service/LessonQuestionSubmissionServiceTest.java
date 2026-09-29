package com.drivingschool.backend.lesson.question.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.lesson.question.dto.QuestionResponse;
import com.drivingschool.backend.lesson.question.dto.RespondToQuestionRequest;
import com.drivingschool.backend.lesson.question.dto.SubmitQuestionRequest;
import com.drivingschool.backend.lesson.question.dto.UpdateQuestionStatusRequest;
import com.drivingschool.backend.lesson.question.entity.LessonQuestionSubmission;
import com.drivingschool.backend.lesson.question.enums.QuestionStatus;
import com.drivingschool.backend.lesson.question.repository.LessonQuestionStatusHistoryRepository;
import com.drivingschool.backend.lesson.question.repository.LessonQuestionSubmissionRepository;
import com.drivingschool.backend.lesson.question.validator.QuestionValidator;
import com.drivingschool.backend.notification.service.NotificationService;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.school.validator.CallerSchoolScope;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonQuestionSubmissionServiceTest {

    private final AdminSchoolScope adminSchoolScope = mock(AdminSchoolScope.class);
    private final CallerSchoolScope callerSchoolScope = mock(CallerSchoolScope.class);

    @Mock private LessonQuestionSubmissionRepository questionRepository;
    @Mock private LessonQuestionStatusHistoryRepository statusHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    @Mock private LessonQuestionStatusHistoryService statusHistoryService;
    @Mock private NotificationService notificationService;
    private final QuestionValidator validator = new QuestionValidator(adminSchoolScope, callerSchoolScope);

    private LessonQuestionSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new LessonQuestionSubmissionService(questionRepository, statusHistoryRepository,
                userRepository, studentProfileRepository, instructorProfileRepository,
                statusHistoryService, validator, notificationService);
    }

    // Every profile fixture shares one school (with a real id), as real same-school
    // students and instructors do - cross-school checks compare school ids.
    private School defaultSchool() {
        School school = School.builder().active(true).build();
        ReflectionTestUtils.setField(school, "id", 1L);
        return school;
    }

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private StudentProfile studentProfile(Long id, User user) {
        StudentProfile profile = StudentProfile.builder().user(user).school(defaultSchool()).build();
        ReflectionTestUtils.setField(profile, "id", id);
        return profile;
    }

    private InstructorProfile instructorProfile(Long id, User user) {
        InstructorProfile profile = InstructorProfile.builder().user(user).active(true).school(defaultSchool()).build();
        ReflectionTestUtils.setField(profile, "id", id);
        return profile;
    }

    private LessonQuestionSubmission questionWithId(Long id, StudentProfile student, InstructorProfile instructor, QuestionStatus status) {
        LessonQuestionSubmission question = LessonQuestionSubmission.builder()
                .student(student).instructor(instructor).subject("Subject").questionBody("Question body text")
                .status(status).build();
        ReflectionTestUtils.setField(question, "id", id);
        return question;
    }

    // --- submitQuestion ---

    @Test
    void submitQuestion_withoutAssignedInstructor_savesAndRecordsHistory() {
        User studentUser = userWithId(1L);
        StudentProfile student = studentProfile(10L, studentUser);
        SubmitQuestionRequest request = new SubmitQuestionRequest();
        request.setSubject("Subject");
        request.setQuestionBody("Question body text");

        when(studentProfileRepository.findByUserId(1L)).thenReturn(Optional.of(student));
        when(questionRepository.save(any(LessonQuestionSubmission.class))).thenAnswer(invocation -> {
            LessonQuestionSubmission q = invocation.getArgument(0);
            ReflectionTestUtils.setField(q, "id", 100L);
            return q;
        });

        QuestionResponse response = service.submitQuestion(request, 1L);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getStatus()).isEqualTo(QuestionStatus.PENDING);
        assertThat(response.getInstructorId()).isNull();
        verify(statusHistoryService, times(1)).recordStatusChange(
                any(LessonQuestionSubmission.class), eq(null), eq(QuestionStatus.PENDING), eq(studentUser), any());
        verify(instructorProfileRepository, never()).findByUserId(any());
    }

    @Test
    void submitQuestion_withAssignedInstructor_loadsInstructorProfile() {
        User studentUser = userWithId(1L);
        User instructorUser = userWithId(2L);
        StudentProfile student = studentProfile(10L, studentUser);
        InstructorProfile instructor = instructorProfile(20L, instructorUser);
        SubmitQuestionRequest request = new SubmitQuestionRequest();
        request.setSubject("Subject");
        request.setQuestionBody("Question body text");
        request.setAssignedInstructorId(20L);

        when(studentProfileRepository.findByUserId(1L)).thenReturn(Optional.of(student));
        when(instructorProfileRepository.findById(20L)).thenReturn(Optional.of(instructor));
        when(questionRepository.save(any(LessonQuestionSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuestionResponse response = service.submitQuestion(request, 1L);

        assertThat(response.getInstructorId()).isEqualTo(20L);
    }

    @Test
    void submitQuestion_withInvalidRequest_throwsBadRequestException() {
        SubmitQuestionRequest request = new SubmitQuestionRequest();
        request.setSubject("");
        request.setQuestionBody("Question body text");

        assertThatThrownBy(() -> service.submitQuestion(request, 1L))
                .isInstanceOf(BadRequestException.class);

        verify(studentProfileRepository, never()).findByUserId(any());
    }

    @Test
    void submitQuestion_whenStudentProfileMissing_throwsResourceNotFoundException() {
        SubmitQuestionRequest request = new SubmitQuestionRequest();
        request.setSubject("Subject");
        request.setQuestionBody("Question body text");
        when(studentProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitQuestion(request, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitQuestion_whenAssignedInstructorMissing_throwsResourceNotFoundException() {
        StudentProfile student = studentProfile(10L, userWithId(1L));
        SubmitQuestionRequest request = new SubmitQuestionRequest();
        request.setSubject("Subject");
        request.setQuestionBody("Question body text");
        request.setAssignedInstructorId(99L);
        when(studentProfileRepository.findByUserId(1L)).thenReturn(Optional.of(student));
        when(instructorProfileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitQuestion(request, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- respondToQuestion ---

    @Test
    void respondToQuestion_withValidRequest_updatesQuestionAndRecordsHistory() {
        User instructorUser = userWithId(2L);
        InstructorProfile instructor = instructorProfile(20L, instructorUser);
        LessonQuestionSubmission question = questionWithId(100L, studentProfile(10L, userWithId(1L)), instructor, QuestionStatus.PENDING);
        RespondToQuestionRequest request = new RespondToQuestionRequest();
        request.setResponse("Here is the answer to your question");

        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));
        when(instructorProfileRepository.findByUserId(2L)).thenReturn(Optional.of(instructor));
        when(questionRepository.save(any(LessonQuestionSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuestionResponse response = service.respondToQuestion(100L, request, 2L);

        assertThat(response.getStatus()).isEqualTo(QuestionStatus.ANSWERED);
        assertThat(response.getResponse()).isEqualTo("Here is the answer to your question");
        verify(statusHistoryService, times(1)).recordStatusChange(
                any(), eq(QuestionStatus.PENDING), eq(QuestionStatus.ANSWERED), eq(instructorUser), any());
    }

    @Test
    void respondToQuestion_whenQuestionNotFound_throwsResourceNotFoundException() {
        RespondToQuestionRequest request = new RespondToQuestionRequest();
        request.setResponse("Here is the answer to your question");
        when(questionRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.respondToQuestion(100L, request, 2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void respondToQuestion_byUnassignedInstructor_throwsBadRequestException() {
        InstructorProfile assignedInstructor = instructorProfile(20L, userWithId(2L));
        LessonQuestionSubmission question = questionWithId(100L, studentProfile(10L, userWithId(1L)), assignedInstructor, QuestionStatus.PENDING);
        RespondToQuestionRequest request = new RespondToQuestionRequest();
        request.setResponse("Here is the answer to your question");
        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.respondToQuestion(100L, request, 999L))
                .isInstanceOf(BadRequestException.class);

        verify(questionRepository, never()).save(any());
    }

    // --- updateQuestionStatus ---

    @Test
    void updateQuestionStatus_withValidRequest_updatesStatusAndRecordsHistory() {
        User adminUser = userWithId(5L);
        LessonQuestionSubmission question = questionWithId(100L,
                studentProfile(10L, userWithId(1L)), instructorProfile(20L, userWithId(2L)), QuestionStatus.PENDING);
        UpdateQuestionStatusRequest request = new UpdateQuestionStatusRequest();
        request.setNewStatus(QuestionStatus.IN_PROGRESS);
        request.setChangeReason("Picked up");

        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));
        when(questionRepository.save(any(LessonQuestionSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(5L)).thenReturn(Optional.of(adminUser));

        QuestionResponse response = service.updateQuestionStatus(100L, request, 5L, "ADMIN");

        assertThat(response.getStatus()).isEqualTo(QuestionStatus.IN_PROGRESS);
        verify(statusHistoryService, times(1)).recordStatusChange(
                any(), eq(QuestionStatus.PENDING), eq(QuestionStatus.IN_PROGRESS), eq(adminUser), eq("Picked up"));
    }

    @Test
    void updateQuestionStatus_onClosedQuestion_throwsBadRequestException() {
        LessonQuestionSubmission question = questionWithId(100L,
                studentProfile(10L, userWithId(1L)), instructorProfile(20L, userWithId(2L)), QuestionStatus.CLOSED);
        UpdateQuestionStatusRequest request = new UpdateQuestionStatusRequest();
        request.setNewStatus(QuestionStatus.IN_PROGRESS);
        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.updateQuestionStatus(100L, request, 5L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);

        verify(questionRepository, never()).save(any());
    }

    @Test
    void updateQuestionStatus_whenChangedByUserMissing_throwsResourceNotFoundException() {
        LessonQuestionSubmission question = questionWithId(100L,
                studentProfile(10L, userWithId(1L)), instructorProfile(20L, userWithId(2L)), QuestionStatus.PENDING);
        UpdateQuestionStatusRequest request = new UpdateQuestionStatusRequest();
        request.setNewStatus(QuestionStatus.IN_PROGRESS);
        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));
        when(questionRepository.save(any(LessonQuestionSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateQuestionStatus(100L, request, 5L, "ADMIN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- getQuestion ---

    @Test
    void getQuestion_whenNotFound_throwsResourceNotFoundException() {
        when(questionRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getQuestion(100L, 1L, "STUDENT"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getQuestion_asUnrelatedStudent_throwsBadRequestException() {
        LessonQuestionSubmission question = questionWithId(100L,
                studentProfile(10L, userWithId(1L)), instructorProfile(20L, userWithId(2L)), QuestionStatus.PENDING);
        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.getQuestion(100L, 999L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getQuestion_asOwningStudent_returnsResponse() {
        LessonQuestionSubmission question = questionWithId(100L,
                studentProfile(10L, userWithId(1L)), instructorProfile(20L, userWithId(2L)), QuestionStatus.PENDING);
        when(questionRepository.findById(100L)).thenReturn(Optional.of(question));

        QuestionResponse response = service.getQuestion(100L, 1L, "STUDENT");

        assertThat(response.getId()).isEqualTo(100L);
    }

    // --- getStudentQuestions / getInstructorQuestions / getQuestionsByStatus / getInstructorPendingQuestions ---

    @Test
    void getStudentQuestions_resolvesProfileIdFromUserId() {
        StudentProfile student = studentProfile(10L, userWithId(1L));
        LessonQuestionSubmission question = questionWithId(100L, student, null, QuestionStatus.PENDING);
        Pageable pageable = Pageable.unpaged();
        when(studentProfileRepository.findByUserId(1L)).thenReturn(Optional.of(student));
        when(questionRepository.findByStudentId(eq(10L), any())).thenReturn(new PageImpl<>(java.util.List.of(question)));

        Page<QuestionResponse> result = service.getStudentQuestions(1L, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(questionRepository, times(1)).findByStudentId(eq(10L), any());
    }

    @Test
    void getStudentQuestions_whenCallerHasNoStudentProfile_throwsResourceNotFoundException() {
        Pageable pageable = Pageable.unpaged();
        when(studentProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStudentQuestions(1L, pageable))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(questionRepository, never()).findByStudentId(any(), any());
    }

    @Test
    void getInstructorQuestions_resolvesProfileIdFromUserId() {
        InstructorProfile instructor = instructorProfile(20L, userWithId(2L));
        Pageable pageable = Pageable.unpaged();
        when(instructorProfileRepository.findByUserId(2L)).thenReturn(Optional.of(instructor));
        when(questionRepository.findByInstructorId(eq(20L), any())).thenReturn(new PageImpl<>(java.util.List.of()));

        service.getInstructorQuestions(2L, pageable);

        verify(questionRepository, times(1)).findByInstructorId(eq(20L), any());
    }

    @Test
    void getInstructorQuestions_whenCallerHasNoInstructorProfile_throwsResourceNotFoundException() {
        Pageable pageable = Pageable.unpaged();
        when(instructorProfileRepository.findByUserId(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInstructorQuestions(2L, pageable))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(questionRepository, never()).findByInstructorId(any(), any());
    }

    @Test
    void getQuestionsByStatus_unrestrictedCaller_delegatesDirectlyToRepository() {
        Pageable pageable = Pageable.unpaged();
        when(questionRepository.findByStatus(eq(QuestionStatus.PENDING), any())).thenReturn(new PageImpl<>(java.util.List.of()));

        service.getQuestionsByStatus(QuestionStatus.PENDING, pageable);

        verify(questionRepository, times(1)).findByStatus(eq(QuestionStatus.PENDING), any());
    }

    @Test
    void getInstructorPendingQuestions_includesMineAndMySchoolsUnclaimedOnes() {
        InstructorProfile instructor = instructorProfile(20L, userWithId(2L));
        Pageable pageable = Pageable.unpaged();
        when(instructorProfileRepository.findByUserId(2L)).thenReturn(Optional.of(instructor));
        when(questionRepository.findInstructorInbox(eq(20L), eq(1L), eq(QuestionStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(java.util.List.of()));

        service.getInstructorPendingQuestions(2L, pageable);

        verify(questionRepository, times(1)).findInstructorInbox(eq(20L), eq(1L), eq(QuestionStatus.PENDING), any());
    }

    @Test
    void getInstructorPendingQuestions_whenCallerHasNoInstructorProfile_throwsResourceNotFoundException() {
        Pageable pageable = Pageable.unpaged();
        when(instructorProfileRepository.findByUserId(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInstructorPendingQuestions(2L, pageable))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(questionRepository, never()).findByInstructorAndStatus(any(), any(), any());
    }

    @Test
    void getQuestionsByStatus_asRegularAdminOrInstructor_isFilteredToTheirSchool() {
        when(callerSchoolScope.callerSchoolId()).thenReturn(Optional.of(7L));
        when(questionRepository.findByStatusAndSchoolId(eq(QuestionStatus.PENDING), eq(7L), any()))
                .thenReturn(new PageImpl<>(java.util.List.of()));

        service.getQuestionsByStatus(QuestionStatus.PENDING, Pageable.unpaged());

        verify(questionRepository, never()).findByStatus(any(), any());
    }

    @Test
    void submitQuestion_assignedToInstructorOfAnotherSchool_throwsBadRequestException() {
        StudentProfile student = studentProfile(10L, userWithId(1L));
        InstructorProfile instructor = instructorProfile(20L, userWithId(2L));
        School otherSchool = School.builder().active(true).build();
        ReflectionTestUtils.setField(otherSchool, "id", 2L);
        ReflectionTestUtils.setField(instructor, "school", otherSchool);
        SubmitQuestionRequest request = new SubmitQuestionRequest();
        request.setSubject("Subject");
        request.setQuestionBody("Question body text");
        request.setAssignedInstructorId(20L);

        when(studentProfileRepository.findByUserId(1L)).thenReturn(Optional.of(student));
        when(instructorProfileRepository.findById(20L)).thenReturn(Optional.of(instructor));

        assertThatThrownBy(() -> service.submitQuestion(request, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("own school");
        verify(questionRepository, never()).save(any());
    }

    // --- unassigned questions reach the school's instructors ---

    @Test
    void submitQuestion_unassigned_notifiesEveryActiveInstructorOfTheSchool() {
        StudentProfile student = studentProfile(10L, userWithId(1L));
        InstructorProfile active1 = instructorProfile(20L, userWithId(2L));
        InstructorProfile active2 = instructorProfile(21L, userWithId(3L));
        InstructorProfile inactive = instructorProfile(22L, userWithId(4L));
        inactive.setActive(false);
        SubmitQuestionRequest request = new SubmitQuestionRequest();
        request.setSubject("Roundabouts");
        request.setQuestionBody("Who has priority on a mini roundabout?");

        when(studentProfileRepository.findByUserId(1L)).thenReturn(Optional.of(student));
        when(questionRepository.save(any(LessonQuestionSubmission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(instructorProfileRepository.findBySchoolIdExcludingDeletedUsers(1L)).thenReturn(java.util.List.of(active1, active2, inactive));

        service.submitQuestion(request, 1L);

        org.mockito.ArgumentCaptor<com.drivingschool.backend.notification.dto.SendNotificationRequest> sent =
                org.mockito.ArgumentCaptor.forClass(com.drivingschool.backend.notification.dto.SendNotificationRequest.class);
        verify(notificationService, times(2)).sendAfterCommit(sent.capture());
        assertThat(sent.getAllValues()).extracting(r -> r.getUserId()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void respondToQuestion_unassigned_isClaimedByTheRespondingInstructor() {
        StudentProfile student = studentProfile(10L, userWithId(1L));
        InstructorProfile responder = instructorProfile(20L, userWithId(2L));
        LessonQuestionSubmission question = LessonQuestionSubmission.builder()
                .student(student).subject("Mirrors").questionBody("How often?").status(QuestionStatus.PENDING).build();
        RespondToQuestionRequest request = new RespondToQuestionRequest();
        request.setResponse("Every five to eight seconds.");

        when(questionRepository.findById(5L)).thenReturn(Optional.of(question));
        when(instructorProfileRepository.findByUserId(2L)).thenReturn(Optional.of(responder));
        when(questionRepository.save(any(LessonQuestionSubmission.class))).thenAnswer(inv -> inv.getArgument(0));

        service.respondToQuestion(5L, request, 2L);

        assertThat(question.getInstructor()).isSameAs(responder);
    }

    @Test
    void updateQuestionStatus_instructorPickingUpAnUnassignedQuestion_claimsIt() {
        StudentProfile student = studentProfile(10L, userWithId(1L));
        InstructorProfile instructor = instructorProfile(20L, userWithId(2L));
        LessonQuestionSubmission question = LessonQuestionSubmission.builder()
                .student(student).subject("Mirrors").questionBody("How often?").status(QuestionStatus.PENDING).build();
        UpdateQuestionStatusRequest request = new UpdateQuestionStatusRequest();
        request.setNewStatus(QuestionStatus.IN_PROGRESS);

        when(questionRepository.findById(5L)).thenReturn(Optional.of(question));
        when(instructorProfileRepository.findByUserId(2L)).thenReturn(Optional.of(instructor));
        when(questionRepository.save(any(LessonQuestionSubmission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(2L)).thenReturn(Optional.of(userWithId(2L)));

        service.updateQuestionStatus(5L, request, 2L, "INSTRUCTOR");

        assertThat(question.getInstructor()).isSameAs(instructor);
    }
}
