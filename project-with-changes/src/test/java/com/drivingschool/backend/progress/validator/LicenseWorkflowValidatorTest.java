package com.drivingschool.backend.progress.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.progress.entity.LicenseWorkflow;
import com.drivingschool.backend.progress.enums.LicenseStage;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LicenseWorkflowValidatorTest {

    private final LicenseWorkflowValidator validator = new LicenseWorkflowValidator();

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private LicenseWorkflow workflowFor(User studentUser) {
        StudentProfile student = StudentProfile.builder().user(studentUser).school(School.builder().active(true).build()).build();
        return LicenseWorkflow.builder()
                .student(student)
                .currentStage(LicenseStage.THEORY_LEARNING)
                .theoryProgressPercent(0)
                .roadTrainingHours(0)
                .stageUpdatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void validateStudentAccess_owningStudent_allowed() {
        LicenseWorkflow workflow = workflowFor(userWithId(1L));

        assertThatCode(() -> validator.validateStudentAccess(workflow, 1L, "STUDENT")).doesNotThrowAnyException();
    }

    @Test
    void validateStudentAccess_differentStudent_denied() {
        LicenseWorkflow workflow = workflowFor(userWithId(1L));

        assertThatThrownBy(() -> validator.validateStudentAccess(workflow, 999L, "STUDENT"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateStudentAccess_admin_alwaysAllowed() {
        LicenseWorkflow workflow = workflowFor(userWithId(1L));

        assertThatCode(() -> validator.validateStudentAccess(workflow, 999L, "ADMIN")).doesNotThrowAnyException();
    }

    @Test
    void validateStudentAccess_unrelatedInstructor_stillAllowed() {
        // Intentional: no assigned-instructor relationship exists in the data model,
        // so instructor access is left as broad school-staff privilege.
        LicenseWorkflow workflow = workflowFor(userWithId(1L));

        assertThatCode(() -> validator.validateStudentAccess(workflow, 999L, "INSTRUCTOR")).doesNotThrowAnyException();
    }
}
