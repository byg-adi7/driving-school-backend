package com.drivingschool.backend.live.validator;

import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.live.entity.LiveSession;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import org.springframework.stereotype.Component;

@Component
public class LiveSessionValidator {

    private final AdminSchoolScope adminSchoolScope;

    public LiveSessionValidator(AdminSchoolScope adminSchoolScope) {
        this.adminSchoolScope = adminSchoolScope;
    }

    /** For scheduling a session "as" a specific instructor - non-admins may only schedule as themselves. */
    public void validateInstructorSelf(InstructorProfile instructor, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            adminSchoolScope.requireAccess(instructor.getSchool().getId());
            return;
        }
        if ("INSTRUCTOR".equals(role) && instructor.getUser().getId().equals(userId)) {
            return;
        }
        throw new ForbiddenException("You can only schedule sessions under your own instructor profile");
    }

    /**
     * LiveSessionResponse includes the meeting URL, so this closes a real cross-tenant
     * leak: without this check any authenticated user at any school could view - and
     * join - another school's live session.
     */
    public void validateSchoolAccess(Long targetSchoolId, Long callerSchoolId, String role) {
        if ("ADMIN".equals(role)) {
            adminSchoolScope.requireAccess(targetSchoolId);
            return;
        }
        if (targetSchoolId != null && targetSchoolId.equals(callerSchoolId)) {
            return;
        }
        throw new ForbiddenException("You do not have access to this school's sessions");
    }

    /** For mutating/viewing a specific session's status or roster - only its own instructor, or ADMIN. */
    public void validateInstructorOwnership(LiveSession session, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            adminSchoolScope.requireAccess(session.getSchool().getId());
            return;
        }
        if ("INSTRUCTOR".equals(role) && session.getInstructor().getUser().getId().equals(userId)) {
            return;
        }
        throw new ForbiddenException("You are not authorized to manage this session");
    }
}
