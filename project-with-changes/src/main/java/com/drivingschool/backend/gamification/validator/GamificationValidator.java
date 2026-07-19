package com.drivingschool.backend.gamification.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.student.entity.StudentProfile;
import org.springframework.stereotype.Component;

/**
 * Gamification data (points/streak/badges) isn't sensitive coaching content like a
 * LessonNote, so instructor access is scoped to "same school" rather than "has taught
 * this student" - a proportionate check that avoids pulling a BookingRepository
 * dependency into this package purely for an access check.
 */
@Component
public class GamificationValidator {

    public void validateStudentSummaryAccess(StudentProfile student, Long userId, String role, Long callerSchoolId) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if ("STUDENT".equals(role) && student.getUser().getId().equals(userId)) {
            return;
        }
        if ("INSTRUCTOR".equals(role) && callerSchoolId != null && callerSchoolId.equals(student.getSchool().getId())) {
            return;
        }
        throw new BadRequestException("You do not have access to this student's gamification summary");
    }

    public void validateLeaderboardAccess(Long schoolId, String role, Long callerSchoolId) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if (callerSchoolId != null && callerSchoolId.equals(schoolId)) {
            return;
        }
        throw new BadRequestException("You do not have access to this school's leaderboard");
    }
}
