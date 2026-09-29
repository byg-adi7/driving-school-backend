package com.drivingschool.backend.progress.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.progress.entity.LicenseWorkflow;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.student.entity.StudentProfile;
import org.springframework.stereotype.Component;

@Component
public class LicenseWorkflowValidator {

    private final AdminSchoolScope adminSchoolScope;

    public LicenseWorkflowValidator(AdminSchoolScope adminSchoolScope) {
        this.adminSchoolScope = adminSchoolScope;
    }

    /**
     * INSTRUCTOR is intentionally left unrestricted - there is no assigned-instructor
     * relationship in the data model to scope against, so this is accepted as broad
     * school-staff privilege. A regular ADMIN is confined to their own school.
     * STUDENT may only act on their own workflow.
     */
    public void validateStudentAccess(LicenseWorkflow workflow, Long userId, String role) {
        validateAdminSchoolAccess(workflow.getStudent());
        if ("STUDENT".equals(role) && !workflow.getStudent().getUser().getId().equals(userId)) {
            throw new BadRequestException("You do not have access to this student's license workflow");
        }
    }

    /**
     * For the ADMIN-only endpoints that take no role parameter. A no-op for non-ADMIN
     * callers - markQuizPassed also runs inside a STUDENT's own quiz submission.
     */
    public void validateAdminSchoolAccess(StudentProfile student) {
        adminSchoolScope.requireAccess(student.getSchool().getId());
    }
}
