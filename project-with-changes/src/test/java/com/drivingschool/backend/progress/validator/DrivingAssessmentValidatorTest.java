package com.drivingschool.backend.progress.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.progress.entity.DrivingAssessment;
import com.drivingschool.backend.progress.enums.AssessmentResult;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class DrivingAssessmentValidatorTest {

    private final AdminSchoolScope adminSchoolScope = mock(AdminSchoolScope.class);

    private final DrivingAssessmentValidator validator = new DrivingAssessmentValidator(adminSchoolScope);

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private DrivingAssessment assessmentFor(User instructorUser, User studentUser) {
        InstructorProfile instructor = InstructorProfile.builder().user(instructorUser).active(true).school(School.builder().active(true).build()).build();
        StudentProfile student = StudentProfile.builder().user(studentUser).school(School.builder().active(true).build()).build();
        return DrivingAssessment.builder().instructor(instructor).student(student)
                .assessmentDate(LocalDateTime.now()).score(80).result(AssessmentResult.PASSED).build();
    }

    // --- validateOwnership ---

    @Test
    void validateOwnership_whenCallerIsAuthoringInstructor_doesNotThrow() {
        DrivingAssessment assessment = assessmentFor(userWithId(1L), userWithId(2L));

        assertThatNoException().isThrownBy(() -> validator.validateOwnership(assessment, 1L));
    }

    @Test
    void validateOwnership_whenCallerIsDifferentInstructor_throwsBadRequestException() {
        DrivingAssessment assessment = assessmentFor(userWithId(1L), userWithId(2L));

        assertThatThrownBy(() -> validator.validateOwnership(assessment, 999L))
                .isInstanceOf(BadRequestException.class);
    }

    // --- validateReadAccess ---

    @Test
    void validateReadAccess_adminOfSameSchool_allowed() {
        DrivingAssessment assessment = assessmentFor(userWithId(1L), userWithId(2L));

        assertThatNoException().isThrownBy(() -> validator.validateReadAccess(assessment, 999L, "ADMIN"));
    }

    @Test
    void validateReadAccess_authoringInstructor_allowed() {
        DrivingAssessment assessment = assessmentFor(userWithId(1L), userWithId(2L));

        assertThatNoException().isThrownBy(() -> validator.validateReadAccess(assessment, 1L, "INSTRUCTOR"));
    }

    @Test
    void validateReadAccess_unrelatedInstructor_denied() {
        DrivingAssessment assessment = assessmentFor(userWithId(1L), userWithId(2L));

        assertThatThrownBy(() -> validator.validateReadAccess(assessment, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateReadAccess_theAssessmentStudent_allowed() {
        DrivingAssessment assessment = assessmentFor(userWithId(1L), userWithId(2L));

        assertThatNoException().isThrownBy(() -> validator.validateReadAccess(assessment, 2L, "STUDENT"));
    }

    @Test
    void validateReadAccess_unrelatedStudent_denied() {
        DrivingAssessment assessment = assessmentFor(userWithId(1L), userWithId(2L));

        assertThatThrownBy(() -> validator.validateReadAccess(assessment, 999L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);
    }

    // --- validateStudentAssessmentsAccess ---

    @Test
    void validateStudentAssessmentsAccess_self_allowed() {
        StudentProfile student = StudentProfile.builder().user(userWithId(2L)).school(School.builder().active(true).build()).build();

        assertThatNoException().isThrownBy(() -> validator.validateStudentAssessmentsAccess(student, 2L, "STUDENT", false));
    }

    @Test
    void validateStudentAssessmentsAccess_differentStudent_denied() {
        StudentProfile student = StudentProfile.builder().user(userWithId(2L)).school(School.builder().active(true).build()).build();

        assertThatThrownBy(() -> validator.validateStudentAssessmentsAccess(student, 999L, "STUDENT", false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateStudentAssessmentsAccess_instructorWhoHasTaughtStudent_allowed() {
        StudentProfile student = StudentProfile.builder().user(userWithId(2L)).school(School.builder().active(true).build()).build();

        assertThatNoException().isThrownBy(() -> validator.validateStudentAssessmentsAccess(student, 5L, "INSTRUCTOR", true));
    }

    @Test
    void validateStudentAssessmentsAccess_instructorWhoNeverTaughtStudent_denied() {
        StudentProfile student = StudentProfile.builder().user(userWithId(2L)).school(School.builder().active(true).build()).build();

        assertThatThrownBy(() -> validator.validateStudentAssessmentsAccess(student, 5L, "INSTRUCTOR", false))
                .isInstanceOf(BadRequestException.class);
    }

    // --- validateInstructorAssessmentsAccess ---

    @Test
    void validateInstructorAssessmentsAccess_self_allowed() {
        InstructorProfile instructor = InstructorProfile.builder().user(userWithId(1L)).active(true).school(School.builder().active(true).build()).build();

        assertThatNoException().isThrownBy(() -> validator.validateInstructorAssessmentsAccess(instructor, 1L, "INSTRUCTOR"));
    }

    @Test
    void validateInstructorAssessmentsAccess_differentInstructor_denied() {
        InstructorProfile instructor = InstructorProfile.builder().user(userWithId(1L)).active(true).school(School.builder().active(true).build()).build();

        assertThatThrownBy(() -> validator.validateInstructorAssessmentsAccess(instructor, 999L, "INSTRUCTOR"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateReadAccess_adminOfAnotherSchool_denied() {
        DrivingAssessment assessment = assessmentFor(userWithId(1L), userWithId(2L));
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(any());

        assertThatThrownBy(() -> validator.validateReadAccess(assessment, 999L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateStudentAssessmentsAccess_adminOfAnotherSchool_denied() {
        DrivingAssessment assessment = assessmentFor(userWithId(1L), userWithId(2L));
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(any());

        assertThatThrownBy(() -> validator.validateStudentAssessmentsAccess(assessment.getStudent(), 999L, "ADMIN", false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateInstructorAssessmentsAccess_adminOfAnotherSchool_denied() {
        DrivingAssessment assessment = assessmentFor(userWithId(1L), userWithId(2L));
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(any());

        assertThatThrownBy(() -> validator.validateInstructorAssessmentsAccess(assessment.getInstructor(), 999L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
    }
}
