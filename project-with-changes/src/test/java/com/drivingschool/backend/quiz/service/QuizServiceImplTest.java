package com.drivingschool.backend.quiz.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.gamification.service.GamificationService;
import com.drivingschool.backend.learning.entity.Course;
import com.drivingschool.backend.learning.repository.CourseRepository;
import com.drivingschool.backend.notification.service.NotificationService;
import com.drivingschool.backend.progress.repository.LicenseWorkflowRepository;
import com.drivingschool.backend.progress.service.LicenseWorkflowService;
import com.drivingschool.backend.quiz.dto.CreateQuizQuestionRequest;
import com.drivingschool.backend.quiz.dto.CreateQuizRequest;
import com.drivingschool.backend.quiz.entity.Quiz;
import com.drivingschool.backend.quiz.entity.QuizQuestion;
import com.drivingschool.backend.quiz.enums.QuestionType;
import com.drivingschool.backend.quiz.mapper.QuizMapper;
import com.drivingschool.backend.quiz.repository.QuizQuestionRepository;
import com.drivingschool.backend.quiz.repository.QuizRepository;
import com.drivingschool.backend.quiz.repository.QuizSubmissionRepository;
import com.drivingschool.backend.quiz.dto.SubmitQuizRequest;
import com.drivingschool.backend.quiz.validator.QuizValidator;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceImplTest {

    private final AdminSchoolScope adminSchoolScope = mock(AdminSchoolScope.class);

    @Mock private QuizRepository quizRepository;
    @Mock private QuizQuestionRepository quizQuestionRepository;
    @Mock private QuizSubmissionRepository quizSubmissionRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private LicenseWorkflowService licenseWorkflowService;
    @Mock private LicenseWorkflowRepository licenseWorkflowRepository;
    @Mock private NotificationService notificationService;
    @Mock private GamificationService gamificationService;
    private final QuizMapper quizMapper = new QuizMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QuizValidator validator = new QuizValidator(adminSchoolScope);

    private QuizServiceImpl quizService;

    @BeforeEach
    void setUp() {
        quizService = new QuizServiceImpl(quizRepository, quizQuestionRepository, quizSubmissionRepository,
                courseRepository, studentProfileRepository, quizMapper, licenseWorkflowService,
                licenseWorkflowRepository, objectMapper, validator, notificationService, gamificationService);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Course courseFor(User instructorUser) {
        InstructorProfile instructor = InstructorProfile.builder().user(instructorUser).active(true).school(School.builder().active(true).build()).build();
        return Course.builder().title("Road Safety 101").instructor(instructor).build();
    }

    private Quiz quizFor(User instructorUser, boolean published) {
        Quiz quiz = Quiz.builder().course(courseFor(instructorUser)).title("Final Exam")
                .passingScore(70).maxAttempts(3).published(published).build();
        ReflectionTestUtils.setField(quiz, "id", 10L);
        return quiz;
    }

    private StudentProfile studentProfile(Long profileId, User user) {
        StudentProfile student = StudentProfile.builder().user(user).school(School.builder().active(true).build()).build();
        ReflectionTestUtils.setField(student, "id", profileId);
        return student;
    }

    // --- create ---

    @Test
    void create_asOwningInstructor_isAllowed() {
        Course course = courseFor(userWithId(1L));
        ReflectionTestUtils.setField(course, "id", 5L);
        CreateQuizRequest request = CreateQuizRequest.builder().courseId(5L).title("Exam").passingScore(70).maxAttempts(3).build();

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> quizService.create(request, 1L, "INSTRUCTOR")).doesNotThrowAnyException();
    }

    @Test
    void create_asUnrelatedInstructor_isDenied() {
        Course course = courseFor(userWithId(1L));
        ReflectionTestUtils.setField(course, "id", 5L);
        CreateQuizRequest request = CreateQuizRequest.builder().courseId(5L).title("Exam").passingScore(70).maxAttempts(3).build();

        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> quizService.create(request, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);

        verify(quizRepository, never()).save(any());
    }

    // --- addQuestion ---

    @Test
    void addQuestion_asUnrelatedInstructor_isDenied() {
        Quiz quiz = quizFor(userWithId(1L), false);
        CreateQuizQuestionRequest request = CreateQuizQuestionRequest.builder()
                .questionText("What does a red light mean?").questionType(QuestionType.MULTIPLE_CHOICE)
                .correctAnswer("Stop").points(10).questionOrder(1).build();

        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> quizService.addQuestion(10L, request, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);

        verify(quizQuestionRepository, never()).save(any());
    }

    // --- publish ---

    @Test
    void publish_asUnrelatedInstructor_isDenied() {
        Quiz quiz = quizFor(userWithId(1L), false);
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> quizService.publish(10L, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);

        verify(quizRepository, never()).save(any());
    }

    // --- getById ---

    @Test
    void getById_draftQuiz_deniedToUnrelatedStudent() {
        Quiz quiz = quizFor(userWithId(1L), false);
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> quizService.getById(10L, true, 999L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getById_publishedQuiz_allowedForAnyStudent() {
        Quiz quiz = quizFor(userWithId(1L), true);
        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findByQuizIdOrderByQuestionOrderAsc(10L)).thenReturn(java.util.List.of());

        assertThatCode(() -> quizService.getById(10L, true, 999L, "STUDENT")).doesNotThrowAnyException();
    }

    // --- submit: the critical fix ---

    @Test
    void submit_asStudent_ignoresRequestBodyStudentIdAndUsesCallerIdentity() {
        Quiz quiz = quizFor(userWithId(1L), true);
        User callingStudentUser = userWithId(2L);
        StudentProfile callingStudent = studentProfile(20L, callingStudentUser);
        // request body claims to submit as a completely different student (60L)
        SubmitQuizRequest request = SubmitQuizRequest.builder().studentId(60L).answers(Map.of(1L, "Stop")).build();

        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(callingStudent));
        when(quizQuestionRepository.findByQuizIdOrderByQuestionOrderAsc(10L)).thenReturn(java.util.List.of());
        when(quizSubmissionRepository.countByQuizIdAndStudentId(10L, 20L)).thenReturn(0L);
        when(quizSubmissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = quizService.submit(10L, request, 2L, "STUDENT");

        assertThat(response.getStudentId()).isEqualTo(20L);
        verify(studentProfileRepository, never()).findById(60L);
    }

    @Test
    void submit_asAdmin_honorsRequestBodyStudentId() {
        Quiz quiz = quizFor(userWithId(1L), true);
        StudentProfile targetStudent = studentProfile(60L, userWithId(6L));
        SubmitQuizRequest request = SubmitQuizRequest.builder().studentId(60L).answers(Map.of(1L, "Stop")).build();

        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(studentProfileRepository.findById(60L)).thenReturn(Optional.of(targetStudent));
        when(quizQuestionRepository.findByQuizIdOrderByQuestionOrderAsc(10L)).thenReturn(java.util.List.of());
        when(quizSubmissionRepository.countByQuizIdAndStudentId(10L, 60L)).thenReturn(0L);
        when(quizSubmissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = quizService.submit(10L, request, 999L, "ADMIN");

        assertThat(response.getStudentId()).isEqualTo(60L);
        verify(studentProfileRepository, never()).findByUserId(anyLong());
    }

    @Test
    void submit_studentWithNoProfile_throwsResourceNotFoundException() {
        Quiz quiz = quizFor(userWithId(1L), true);
        SubmitQuizRequest request = SubmitQuizRequest.builder().studentId(60L).answers(Map.of(1L, "Stop")).build();

        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizService.submit(10L, request, 2L, "STUDENT"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submit_asAdminOfAnotherSchool_isDeniedBeforeScoring() {
        Quiz quiz = quizFor(userWithId(1L), true);
        SubmitQuizRequest request = SubmitQuizRequest.builder().studentId(60L).answers(Map.of(1L, "Stop")).build();

        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(studentProfileRepository.findById(60L)).thenReturn(Optional.of(studentProfile(60L, userWithId(6L))));
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(any());

        assertThatThrownBy(() -> quizService.submit(10L, request, 999L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
        verify(quizSubmissionRepository, never()).save(any());
    }
}
