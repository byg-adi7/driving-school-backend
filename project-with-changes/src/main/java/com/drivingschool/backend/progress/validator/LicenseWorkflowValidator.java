package com.drivingschool.backend.progress.validator;

import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.progress.entity.LicenseWorkflow;
import com.drivingschool.backend.school.validator.CallerSchoolScope;
import com.drivingschool.backend.student.entity.StudentProfile;
import org.springframework.stereotype.Component;

@Component
public class LicenseWorkflowValidator {

    private final CallerSchoolScope callerSchoolScope;

    public LicenseWorkflowValidator(CallerSchoolScope callerSchoolScope) {
        this.callerSchoolScope = callerSchoolScope;
    }

    /**
     * Every caller is confined to the student's school. Within it, INSTRUCTOR is
     * intentionally unrestricted - there is no assigned-instructor relationship in the
     * data model to scope against, so this is accepted as school-staff privilege.
     * STUDENT may only act on their own workflow.
     */
    public void validateStudentAccess(LicenseWorkflow workflow, Long userId, String role) {
        validateSchoolAccess(workflow.getStudent());
        if ("STUDENT".equals(role) && !workflow.getStudent().getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have access to this student's license workflow");
        }
    }

    /**
     * For the endpoints that take no role parameter. Holds for every role - including
     * the STUDENT whose own quiz submission calls markQuizPassed, whose school is by
     * definition the workflow's.
     */
    public void validateSchoolAccess(StudentProfile student) {
        callerSchoolScope.requireSameSchool(student.getSchool().getId());
    }
}
