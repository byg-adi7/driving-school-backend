package com.drivingschool.backend.quiz.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.learning.entity.Course;
import com.drivingschool.backend.quiz.entity.Quiz;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuizValidatorTest {

    private final QuizValidator validator = new QuizValidator();

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
        return Quiz.builder().course(courseFor(instructorUser)).title("Final Exam").published(published).build();
    }

    // --- validateCourseOwnership ---

    @Test
    void validateCourseOwnership_owningInstructor_allowed() {
        Course course = courseFor(userWithId(1L));

        assertThatCode(() -> validator.validateCourseOwnership(course, 1L, "INSTRUCTOR")).doesNotThrowAnyException();
    }

    @Test
    void validateCourseOwnership_differentInstructor_denied() {
        Course course = courseFor(userWithId(1L));

        assertThatThrownBy(() -> validator.validateCourseOwnership(course, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateCourseOwnership_admin_alwaysAllowed() {
        Course course = courseFor(userWithId(1L));

        assertThatCode(() -> validator.validateCourseOwnership(course, 999L, "ADMIN")).doesNotThrowAnyException();
    }

    @Test
    void validateCourseOwnership_student_denied() {
        Course course = courseFor(userWithId(1L));

        assertThatThrownBy(() -> validator.validateCourseOwnership(course, 1L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);
    }

    // --- validateQuizOwnership ---

    @Test
    void validateQuizOwnership_owningInstructor_allowed() {
        Quiz quiz = quizFor(userWithId(1L), false);

        assertThatCode(() -> validator.validateQuizOwnership(quiz, 1L, "INSTRUCTOR")).doesNotThrowAnyException();
    }

    @Test
    void validateQuizOwnership_differentInstructor_denied() {
        Quiz quiz = quizFor(userWithId(1L), false);

        assertThatThrownBy(() -> validator.validateQuizOwnership(quiz, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);
    }

    // --- validateQuizReadAccess ---

    @Test
    void validateQuizReadAccess_publishedQuiz_openToStudent() {
        Quiz quiz = quizFor(userWithId(1L), true);

        assertThatCode(() -> validator.validateQuizReadAccess(quiz, 999L, "STUDENT")).doesNotThrowAnyException();
    }

    @Test
    void validateQuizReadAccess_publishedQuiz_openToUnrelatedInstructor() {
        Quiz quiz = quizFor(userWithId(1L), true);

        assertThatCode(() -> validator.validateQuizReadAccess(quiz, 999L, "INSTRUCTOR")).doesNotThrowAnyException();
    }

    @Test
    void validateQuizReadAccess_draftQuiz_deniedToStudent() {
        Quiz quiz = quizFor(userWithId(1L), false);

        assertThatThrownBy(() -> validator.validateQuizReadAccess(quiz, 999L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateQuizReadAccess_draftQuiz_deniedToUnrelatedInstructor() {
        Quiz quiz = quizFor(userWithId(1L), false);

        assertThatThrownBy(() -> validator.validateQuizReadAccess(quiz, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateQuizReadAccess_draftQuiz_allowedForOwningInstructor() {
        Quiz quiz = quizFor(userWithId(1L), false);

        assertThatCode(() -> validator.validateQuizReadAccess(quiz, 1L, "INSTRUCTOR")).doesNotThrowAnyException();
    }

    @Test
    void validateQuizReadAccess_draftQuiz_allowedForAdmin() {
        Quiz quiz = quizFor(userWithId(1L), false);

        assertThatCode(() -> validator.validateQuizReadAccess(quiz, 999L, "ADMIN")).doesNotThrowAnyException();
    }
}
