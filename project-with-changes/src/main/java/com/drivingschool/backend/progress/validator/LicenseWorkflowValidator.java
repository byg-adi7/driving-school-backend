package com.drivingschool.backend.progress.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.progress.entity.LicenseWorkflow;
import org.springframework.stereotype.Component;

@Component
public class LicenseWorkflowValidator {

    /**
     * ADMIN and INSTRUCTOR are intentionally left unrestricted - there is no
     * assigned-instructor relationship in the data model to scope against, so
     * this is accepted as broad school-staff privilege. STUDENT may only act on
     * their own workflow.
     */
    public void validateStudentAccess(LicenseWorkflow workflow, Long userId, String role) {
        if ("STUDENT".equals(role) && !workflow.getStudent().getUser().getId().equals(userId)) {
            throw new BadRequestException("You do not have access to this student's license workflow");
        }
    }
}
