package com.drivingschool.backend.lesson.note.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.lesson.note.entity.LessonNote;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class LessonNoteValidatorTest {

    private final AdminSchoolScope adminSchoolScope = mock(AdminSchoolScope.class);

    private final LessonNoteValidator validator = new LessonNoteValidator(adminSchoolScope);

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private LessonNote noteFor(User instructorUser, User studentUser) {
        InstructorProfile instructor = InstructorProfile.builder().user(instructorUser).active(true).school(School.builder().active(true).build()).build();
        StudentProfile student = StudentProfile.builder().user(studentUser).school(School.builder().active(true).build()).build();
        return LessonNote.builder().instructor(instructor).student(student).build();
    }

    // --- validateOwnership ---

    @Test
    void validateOwnership_whenCallerIsAuthoringInstructor_doesNotThrow() {
        LessonNote note = noteFor(userWithId(1L), userWithId(2L));

        assertThatNoException().isThrownBy(() -> validator.validateOwnership(note, 1L));
    }

    @Test
    void validateOwnership_whenCallerIsDifferentInstructor_throwsBadRequestException() {
        LessonNote note = noteFor(userWithId(1L), userWithId(2L));

        assertThatThrownBy(() -> validator.validateOwnership(note, 999L))
                .isInstanceOf(ForbiddenException.class);
    }

    // --- validateReadAccess ---

    @Test
    void validateReadAccess_adminOfSameSchool_allowed() {
        LessonNote note = noteFor(userWithId(1L), userWithId(2L));

        assertThatNoException().isThrownBy(() -> validator.validateReadAccess(note, 999L, "ADMIN"));
    }

    @Test
    void validateReadAccess_authoringInstructor_allowed() {
        LessonNote note = noteFor(userWithId(1L), userWithId(2L));

        assertThatNoException().isThrownBy(() -> validator.validateReadAccess(note, 1L, "INSTRUCTOR"));
    }

    @Test
    void validateReadAccess_unrelatedInstructor_denied() {
        LessonNote note = noteFor(userWithId(1L), userWithId(2L));

        assertThatThrownBy(() -> validator.validateReadAccess(note, 999L, "INSTRUCTOR"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void validateReadAccess_theNoteStudent_allowed() {
        LessonNote note = noteFor(userWithId(1L), userWithId(2L));

        assertThatNoException().isThrownBy(() -> validator.validateReadAccess(note, 2L, "STUDENT"));
    }

    @Test
    void validateReadAccess_unrelatedStudent_denied() {
        LessonNote note = noteFor(userWithId(1L), userWithId(2L));

        assertThatThrownBy(() -> validator.validateReadAccess(note, 999L, "STUDENT"))
                .isInstanceOf(ForbiddenException.class);
    }

    // --- validateStudentNotesAccess ---

    @Test
    void validateStudentNotesAccess_self_allowed() {
        StudentProfile student = StudentProfile.builder().user(userWithId(2L)).school(School.builder().active(true).build()).build();

        assertThatNoException().isThrownBy(() -> validator.validateStudentNotesAccess(student, 2L, "STUDENT", false));
    }

    @Test
    void validateStudentNotesAccess_differentStudent_denied() {
        StudentProfile student = StudentProfile.builder().user(userWithId(2L)).school(School.builder().active(true).build()).build();

        assertThatThrownBy(() -> validator.validateStudentNotesAccess(student, 999L, "STUDENT", false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void validateStudentNotesAccess_instructorWhoHasTaughtStudent_allowed() {
        StudentProfile student = StudentProfile.builder().user(userWithId(2L)).school(School.builder().active(true).build()).build();

        assertThatNoException().isThrownBy(() -> validator.validateStudentNotesAccess(student, 5L, "INSTRUCTOR", true));
    }

    @Test
    void validateStudentNotesAccess_instructorWhoNeverTaughtStudent_denied() {
        StudentProfile student = StudentProfile.builder().user(userWithId(2L)).school(School.builder().active(true).build()).build();

        assertThatThrownBy(() -> validator.validateStudentNotesAccess(student, 5L, "INSTRUCTOR", false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void validateStudentNotesAccess_adminOfSameSchool_allowed() {
        StudentProfile student = StudentProfile.builder().user(userWithId(2L)).school(School.builder().active(true).build()).build();

        assertThatNoException().isThrownBy(() -> validator.validateStudentNotesAccess(student, 999L, "ADMIN", false));
    }

    // --- validateInstructorNotesAccess ---

    @Test
    void validateInstructorNotesAccess_self_allowed() {
        InstructorProfile instructor = InstructorProfile.builder().user(userWithId(1L)).active(true).school(School.builder().active(true).build()).build();

        assertThatNoException().isThrownBy(() -> validator.validateInstructorNotesAccess(instructor, 1L, "INSTRUCTOR"));
    }

    @Test
    void validateInstructorNotesAccess_differentInstructor_denied() {
        InstructorProfile instructor = InstructorProfile.builder().user(userWithId(1L)).active(true).school(School.builder().active(true).build()).build();

        assertThatThrownBy(() -> validator.validateInstructorNotesAccess(instructor, 999L, "INSTRUCTOR"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void validateInstructorNotesAccess_adminOfSameSchool_allowed() {
        InstructorProfile instructor = InstructorProfile.builder().user(userWithId(1L)).active(true).school(School.builder().active(true).build()).build();

        assertThatNoException().isThrownBy(() -> validator.validateInstructorNotesAccess(instructor, 999L, "ADMIN"));
    }

    @Test
    void validateReadAccess_adminOfAnotherSchool_denied() {
        LessonNote note = noteFor(userWithId(1L), userWithId(2L));
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(any());

        assertThatThrownBy(() -> validator.validateReadAccess(note, 999L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateAdminSchoolAccess_adminOfAnotherSchool_denied() {
        LessonNote note = noteFor(userWithId(1L), userWithId(2L));
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(any());

        assertThatThrownBy(() -> validator.validateAdminSchoolAccess(note, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
    }
}
