package com.drivingschool.backend.lesson.question.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.lesson.question.entity.LessonQuestionSubmission;
import com.drivingschool.backend.lesson.question.enums.QuestionStatus;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.school.validator.CallerSchoolScope;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionValidatorTest {

    private final AdminSchoolScope adminSchoolScope = mock(AdminSchoolScope.class);
    private final CallerSchoolScope callerSchoolScope = mock(CallerSchoolScope.class);

    private final QuestionValidator validator = new QuestionValidator(adminSchoolScope, callerSchoolScope);

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private LessonQuestionSubmission questionAssignedTo(User instructorUser, User studentUser, QuestionStatus status) {
        InstructorProfile instructor = instructorUser == null ? null :
                InstructorProfile.builder().user(instructorUser).active(true).school(School.builder().active(true).build()).build();
        StudentProfile student = StudentProfile.builder().user(studentUser).school(School.builder().active(true).build()).build();
        return LessonQuestionSubmission.builder().instructor(instructor).student(student).status(status).build();
    }

    // --- validateInstructorAccess ---

    @Test
    void validateInstructorAccess_whenAssignedInstructor_doesNotThrow() {
        LessonQuestionSubmission question = questionAssignedTo(userWithId(1L), userWithId(2L), QuestionStatus.PENDING);

        assertThatCode(() -> validator.validateInstructorAccess(question, 1L)).doesNotThrowAnyException();
    }

    @Test
    void validateInstructorAccess_whenDifferentInstructor_throwsBadRequestException() {
        LessonQuestionSubmission question = questionAssignedTo(userWithId(1L), userWithId(2L), QuestionStatus.PENDING);

        assertThatThrownBy(() -> validator.validateInstructorAccess(question, 999L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateInstructorAccess_whenUnassigned_allowsAnyInstructorOfTheStudentsSchool() {
        LessonQuestionSubmission question = questionAssignedTo(null, userWithId(2L), QuestionStatus.PENDING);

        assertThatCode(() -> validator.validateInstructorAccess(question, 999L)).doesNotThrowAnyException();
    }

    // --- validateStatusUpdate ---

    @Test
    void validateStatusUpdate_asAssignedInstructor_allowed() {
        LessonQuestionSubmission question = questionAssignedTo(userWithId(1L), userWithId(2L), QuestionStatus.PENDING);

        assertThatCode(() -> validator.validateStatusUpdate(question, QuestionStatus.ANSWERED, 1L, "INSTRUCTOR"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateStatusUpdate_asUnassignedInstructor_denied() {
        LessonQuestionSubmission question = questionAssignedTo(userWithId(1L), userWithId(2L), QuestionStatus.PENDING);

        assertThatThrownBy(() -> validator.validateStatusUpdate(question, QuestionStatus.ANSWERED, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not assigned");
    }

    @Test
    void validateStatusUpdate_asStudent_denied() {
        LessonQuestionSubmission question = questionAssignedTo(userWithId(1L), userWithId(2L), QuestionStatus.PENDING);

        assertThatThrownBy(() -> validator.validateStatusUpdate(question, QuestionStatus.ANSWERED, 2L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateStatusUpdate_adminOfSameSchool_bypassesAssignmentCheck() {
        LessonQuestionSubmission question = questionAssignedTo(userWithId(1L), userWithId(2L), QuestionStatus.PENDING);

        assertThatCode(() -> validator.validateStatusUpdate(question, QuestionStatus.ANSWERED, 999L, "ADMIN"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateStatusUpdate_onClosedQuestion_denied() {
        LessonQuestionSubmission question = questionAssignedTo(userWithId(1L), userWithId(2L), QuestionStatus.CLOSED);

        assertThatThrownBy(() -> validator.validateStatusUpdate(question, QuestionStatus.ANSWERED, 1L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("closed");
    }

    // --- validateReadAccess ---

    @Test
    void validateReadAccess_adminOfSameSchool_allowed() {
        LessonQuestionSubmission question = questionAssignedTo(userWithId(1L), userWithId(2L), QuestionStatus.PENDING);

        assertThatCode(() -> validator.validateReadAccess(question, 999L, "ADMIN")).doesNotThrowAnyException();
    }

    @Test
    void validateReadAccess_assignedInstructor_allowed() {
        LessonQuestionSubmission question = questionAssignedTo(userWithId(1L), userWithId(2L), QuestionStatus.PENDING);

        assertThatCode(() -> validator.validateReadAccess(question, 1L, "INSTRUCTOR")).doesNotThrowAnyException();
    }

    @Test
    void validateReadAccess_owningStudent_allowed() {
        LessonQuestionSubmission question = questionAssignedTo(userWithId(1L), userWithId(2L), QuestionStatus.PENDING);

        assertThatCode(() -> validator.validateReadAccess(question, 2L, "STUDENT")).doesNotThrowAnyException();
    }

    @Test
    void validateReadAccess_unrelatedStudent_denied() {
        LessonQuestionSubmission question = questionAssignedTo(userWithId(1L), userWithId(2L), QuestionStatus.PENDING);

        assertThatThrownBy(() -> validator.validateReadAccess(question, 888L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateStatusUpdate_adminOfAnotherSchool_denied() {
        LessonQuestionSubmission question = questionAssignedTo(userWithId(1L), userWithId(2L), QuestionStatus.PENDING);
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(any());

        assertThatThrownBy(() -> validator.validateStatusUpdate(question, QuestionStatus.ANSWERED, 999L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateReadAccess_adminOfAnotherSchool_denied() {
        LessonQuestionSubmission question = questionAssignedTo(userWithId(1L), userWithId(2L), QuestionStatus.PENDING);
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(any());

        assertThatThrownBy(() -> validator.validateReadAccess(question, 999L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateInstructorAccess_unassignedQuestionFromAnotherSchool_denied() {
        LessonQuestionSubmission question = questionAssignedTo(null, userWithId(2L), QuestionStatus.PENDING);
        doThrow(new BadRequestException("no access")).when(callerSchoolScope).requireSameSchool(any());

        assertThatThrownBy(() -> validator.validateInstructorAccess(question, 999L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateStatusUpdate_instructorOnUnassignedQuestionFromAnotherSchool_denied() {
        LessonQuestionSubmission question = questionAssignedTo(null, userWithId(2L), QuestionStatus.PENDING);
        doThrow(new BadRequestException("no access")).when(callerSchoolScope).requireSameSchool(any());

        assertThatThrownBy(() -> validator.validateStatusUpdate(question, QuestionStatus.ANSWERED, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);
    }
}
