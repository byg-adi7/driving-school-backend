package com.drivingschool.backend.quiz.service;

import com.drivingschool.backend.common.transaction.AfterCommit;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.gamification.service.GamificationService;
import com.drivingschool.backend.learning.entity.Course;
import com.drivingschool.backend.learning.repository.CourseRepository;
import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.service.NotificationService;
import com.drivingschool.backend.progress.repository.LicenseWorkflowRepository;
import com.drivingschool.backend.progress.service.LicenseWorkflowService;
import com.drivingschool.backend.quiz.dto.CreateQuizQuestionRequest;
import com.drivingschool.backend.quiz.dto.CreateQuizRequest;
import com.drivingschool.backend.quiz.dto.QuizResponse;
import com.drivingschool.backend.quiz.dto.QuizSubmissionResponse;
import com.drivingschool.backend.quiz.dto.SubmitQuizRequest;
import com.drivingschool.backend.quiz.entity.Quiz;
import com.drivingschool.backend.quiz.entity.QuizQuestion;
import com.drivingschool.backend.quiz.entity.QuizSubmission;
import com.drivingschool.backend.quiz.enums.SubmissionStatus;
import com.drivingschool.backend.quiz.mapper.QuizMapper;
import com.drivingschool.backend.quiz.repository.QuizQuestionRepository;
import com.drivingschool.backend.quiz.repository.QuizRepository;
import com.drivingschool.backend.quiz.repository.QuizSubmissionRepository;
import com.drivingschool.backend.quiz.validator.QuizValidator;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class QuizServiceImpl implements QuizService {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final CourseRepository courseRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final QuizMapper quizMapper;
    private final LicenseWorkflowService licenseWorkflowService;
    private final LicenseWorkflowRepository licenseWorkflowRepository;
    private final ObjectMapper objectMapper;
    private final QuizValidator validator;
    private final NotificationService notificationService;
    private final GamificationService gamificationService;
    private final PublishedQuizCatalog publishedQuizCatalog;

    public QuizServiceImpl(QuizRepository quizRepository,
                           QuizQuestionRepository quizQuestionRepository,
                           QuizSubmissionRepository quizSubmissionRepository,
                           CourseRepository courseRepository,
                           StudentProfileRepository studentProfileRepository,
                           QuizMapper quizMapper,
                           LicenseWorkflowService licenseWorkflowService,
                           LicenseWorkflowRepository licenseWorkflowRepository,
                           ObjectMapper objectMapper,
                           QuizValidator validator,
                           NotificationService notificationService,
                           GamificationService gamificationService,
                           PublishedQuizCatalog publishedQuizCatalog) {
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.courseRepository = courseRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.quizMapper = quizMapper;
        this.licenseWorkflowService = licenseWorkflowService;
        this.licenseWorkflowRepository = licenseWorkflowRepository;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.notificationService = notificationService;
        this.gamificationService = gamificationService;
        this.publishedQuizCatalog = publishedQuizCatalog;
    }

    // A brand-new quiz is always unpublished, so it can't actually appear in
    // getPublishedByCourse yet - evicted anyway for defensiveness, same
    // reasoning as CourseServiceImpl.create().
    @Override
    @Transactional
    @CacheEvict(value = "quizzes-by-course", key = "#request.courseId")
    public QuizResponse create(CreateQuizRequest request, Long userId, String role) {
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", request.getCourseId()));
        validator.validateCourseOwnership(course, userId, role);

        Quiz quiz = Quiz.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .passingScore(request.getPassingScore())
                .timeLimitMinutes(request.getTimeLimitMinutes())
                .maxAttempts(request.getMaxAttempts())
                .published(false)
                .course(course)
                .build();

        Quiz saved = quizRepository.save(quiz);
        return quizMapper.toResponse(saved, List.of(), false);
    }

    // Adding a question to an already-published quiz is rejected below, so in
    // practice this never touches a quiz visible in getPublishedByCourse - kept
    // as a defensive evict (keyed on #result, which Spring resolves after the
    // method returns) in case that invariant ever changes.
    @Override
    @Transactional
    @CacheEvict(value = "quizzes-by-course", key = "#result.courseId")
    public QuizResponse addQuestion(Long quizId, CreateQuizQuestionRequest request, Long userId, String role) {
        Quiz quiz = findQuiz(quizId);
        validator.validateQuizOwnership(quiz, userId, role);
        if (quiz.isPublished()) {
            throw new BadRequestException("Cannot add questions to a published quiz");
        }

        QuizQuestion question = QuizQuestion.builder()
                .questionText(request.getQuestionText())
                .questionType(request.getQuestionType())
                .options(request.getOptions())
                .correctAnswer(request.getCorrectAnswer())
                .points(request.getPoints())
                .questionOrder(request.getQuestionOrder())
                .quiz(quiz)
                .build();

        quizQuestionRepository.save(question);
        List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderByQuestionOrderAsc(quizId);
        return quizMapper.toResponse(quiz, questions, true);
    }

    @Override
    @Transactional
    @CacheEvict(value = "quizzes-by-course", key = "#result.courseId")
    public QuizResponse publish(Long quizId, Long userId, String role) {
        Quiz quiz = findQuiz(quizId);
        validator.validateQuizOwnership(quiz, userId, role);
        List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderByQuestionOrderAsc(quizId);
        if (questions.isEmpty()) {
            throw new BadRequestException("Quiz must have at least one question before publishing");
        }
        quiz.publish();
        Quiz saved = quizRepository.save(quiz);
        return quizMapper.toResponse(saved, questions, true);
    }

    @Override
    @Transactional(readOnly = true)
    public QuizResponse getById(Long quizId, boolean forStudent, Long userId, String role) {
        Quiz quiz = findQuiz(quizId);
        validator.validateQuizReadAccess(quiz, userId, role);
        List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderByQuestionOrderAsc(quizId);
        QuizResponse response = quizMapper.toResponse(quiz, questions, !forStudent);
        if (!"STUDENT".equals(role)) {
            return response;
        }
        // So the app can show "Attempts: 1 of 3 used - Best 60%" and lock Start before
        // the student answers anything, instead of failing on submit.
        return studentProfileRepository.findByUserId(userId)
                .map(student -> response.toBuilder().myAttempts(myAttempts(quiz, student.getId())).build())
                .orElse(response);
    }

    private QuizResponse.MyAttempts myAttempts(Quiz quiz, Long studentId) {
        QuizSubmissionRepository.AttemptSummary summary = quizSubmissionRepository.summarizeAttempts(quiz.getId(), studentId);
        int used = summary == null || summary.getAttempts() == null ? 0 : summary.getAttempts().intValue();
        return QuizResponse.MyAttempts.builder()
                .attemptsUsed(used)
                .attemptsRemaining(Math.max(0, quiz.getMaxAttempts() - used))
                .bestScore(summary == null ? null : summary.getBestScore())
                .passed(summary != null && summary.getPassed() != null && summary.getPassed() == 1)
                .build();
    }

    // The course's school is checked here on every call; only then is the cached,
    // caller-independent list (published-only, answers always stripped) fetched from
    // PublishedQuizCatalog - a cache hit can never skip the school check. getById
    // is intentionally left uncached: its forStudent flag toggles whether the
    // correct answers are included, and caching by quizId alone would risk serving
    // a student a response that was built (and cached) for an instructor's
    // forStudent=false call, leaking answers.
    @Override
    @Transactional(readOnly = true)
    public List<QuizResponse> getPublishedByCourse(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
        validator.validateCourseSchoolAccess(course);
        return publishedQuizCatalog.forCourse(courseId);
    }

    @Override
    @Transactional
    public QuizSubmissionResponse submit(Long quizId, SubmitQuizRequest request, Long userId, String role) {
        Quiz quiz = findQuiz(quizId);
        if (!quiz.isPublished()) {
            throw new BadRequestException("Quiz is not published");
        }

        // STUDENT can only ever submit as themselves - request.getStudentId() is
        // client-supplied and must never be trusted for that role. ADMIN retains the
        // ability to submit on behalf of any student (e.g. manual/paper makeup entry).
        StudentProfile student = "ADMIN".equals(role)
                ? studentProfileRepository.findById(request.getStudentId())
                        .orElseThrow(() -> new ResourceNotFoundException("StudentProfile", "id", request.getStudentId()))
                : studentProfileRepository.findByUserId(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Student profile not found for user ID: " + userId));
        validator.validateAdminSchoolAccess(student);
        if (!student.getSchool().getId().equals(quiz.getCourse().getInstructor().getSchool().getId())) {
            throw new ForbiddenException("This quiz belongs to another school");
        }

        long attempts = quizSubmissionRepository.countByQuizIdAndStudentId(quizId, student.getId());
        if (attempts >= quiz.getMaxAttempts()) {
            throw new BadRequestException("Maximum quiz attempts exceeded");
        }

        List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderByQuestionOrderAsc(quizId);
        int score = calculateScore(questions, request.getAnswers());
        boolean passed = score >= quiz.getPassingScore();

        QuizSubmission submission = QuizSubmission.builder()
                .quiz(quiz)
                .student(student)
                .attemptNumber((int) attempts + 1)
                .answers(serializeAnswers(request.getAnswers()))
                .score(score)
                .passed(passed)
                .status(SubmissionStatus.GRADED)
                .submittedAt(java.time.LocalDateTime.now())
                .build();

        QuizSubmission saved = quizSubmissionRepository.save(submission);

        if (passed && licenseWorkflowRepository.existsByStudentId(student.getId())) {
            licenseWorkflowService.markQuizPassed(student.getId());
        }

        notifyStudentOfResult(quiz, student, saved);

        if (passed) {
            awardQuizPoints(quiz, student);
        }

        log.info("Quiz submitted: quizId={}, studentId={}, score={}, passed={}", quizId, student.getId(), score, passed);
        return quizMapper.toSubmissionResponse(saved);
    }

    // After commit: points only for a submission that was actually saved.
    private void awardQuizPoints(Quiz quiz, StudentProfile student) {
        Long studentId = student.getId();
        Long quizId = quiz.getId();
        AfterCommit.run("award points for quiz " + quizId + " to student " + studentId,
                () -> gamificationService.awardQuizPassed(studentId, quizId));
    }

    private void notifyStudentOfResult(Quiz quiz, StudentProfile student, QuizSubmission submission) {
        try {
            String body = submission.isPassed()
                    ? "You passed \"%s\" with a score of %d. Great work!".formatted(quiz.getTitle(), submission.getScore())
                    : "You did not pass \"%s\" (score %d, %d required). You can try again."
                            .formatted(quiz.getTitle(), submission.getScore(), quiz.getPassingScore());

            SendNotificationRequest request = SendNotificationRequest.builder()
                    .userId(student.getUser().getId())
                    .subject(submission.isPassed() ? "Quiz passed" : "Quiz result")
                    .body(body)
                    .channel(NotificationChannel.IN_APP)
                    .build();
            notificationService.sendAfterCommit(request);
        } catch (Exception ex) {
            log.warn("Failed to send quiz result notification: quizId={}, studentId={}",
                    quiz.getId(), student.getId(), ex);
        }
    }

    private Quiz findQuiz(Long quizId) {
        return quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", "id", quizId));
    }

    private int calculateScore(List<QuizQuestion> questions, Map<Long, String> answers) {
        if (questions.isEmpty()) {
            return 0;
        }
        int totalPoints = questions.stream().mapToInt(QuizQuestion::getPoints).sum();
        int earnedPoints = 0;
        for (QuizQuestion question : questions) {
            String studentAnswer = answers.get(question.getId());
            if (studentAnswer != null && studentAnswer.trim().equalsIgnoreCase(question.getCorrectAnswer().trim())) {
                earnedPoints += question.getPoints();
            }
        }
        return totalPoints == 0 ? 0 : (int) Math.round((earnedPoints * 100.0) / totalPoints);
    }

    private String serializeAnswers(Map<Long, String> answers) {
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Invalid answers format");
        }
    }
}
