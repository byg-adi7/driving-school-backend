package com.drivingschool.backend.learning.validator;

import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.learning.entity.Course;
import com.drivingschool.backend.learning.entity.VideoLesson;
import com.drivingschool.backend.learning.enums.CourseStatus;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.school.validator.CallerSchoolScope;
import org.springframework.stereotype.Component;

@Component
public class LearningValidator {

    private final AdminSchoolScope adminSchoolScope;
    private final CallerSchoolScope callerSchoolScope;

    public LearningValidator(AdminSchoolScope adminSchoolScope, CallerSchoolScope callerSchoolScope) {
        this.adminSchoolScope = adminSchoolScope;
        this.callerSchoolScope = callerSchoolScope;
    }

    /** An ADMIN creating content under an instructor must share that instructor's school. */
    public void validateAdminSchoolAccess(InstructorProfile instructor) {
        adminSchoolScope.requireAccess(instructor.getSchool().getId());
    }

    /**
     * Just the school rule, for listings that apply their own per-item visibility -
     * e.g. a course's lessons, where each lesson's own published flag decides.
     */
    public void validateCourseSchoolAccess(Course course) {
        callerSchoolScope.requireSameSchool(course.getInstructor().getSchool().getId());
    }

    public void validateCourseOwnership(Course course, Long userId, String role) {
        if (!isCourseOwnerOrAdmin(course, userId, role)) {
            throw new ForbiddenException("You are not authorized to manage this course");
        }
    }

    public boolean isCourseOwnerOrAdmin(Course course, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            return adminSchoolScope.canAccess(course.getInstructor().getSchool().getId());
        }
        return "INSTRUCTOR".equals(role) && course.getInstructor().getUser().getId().equals(userId);
    }

    /**
     * A course is only visible within its own school (bootstrap admin: every school).
     * Within it, published courses are open to any authenticated role; draft/archived
     * courses are only visible to the owning instructor or ADMIN.
     */
    public void validateCourseReadAccess(Course course, Long userId, String role) {
        callerSchoolScope.requireSameSchool(course.getInstructor().getSchool().getId());
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            return;
        }
        if ("ADMIN".equals(role)) {
            adminSchoolScope.requireAccess(course.getInstructor().getSchool().getId());
            return;
        }
        if ("INSTRUCTOR".equals(role) && course.getInstructor().getUser().getId().equals(userId)) {
            return;
        }
        throw new ForbiddenException("You do not have access to this course");
    }

    public void validateLessonOwnership(VideoLesson lesson, Long userId, String role) {
        validateCourseOwnership(lesson.getCourse(), userId, role);
    }

    /**
     * Same school rule as courses. Within the school, published lessons are open to any
     * authenticated role (mirrors the quiz module's equivalent policy); unpublished
     * lessons are only visible to the owning instructor or ADMIN.
     */
    public void validateLessonReadAccess(VideoLesson lesson, Long userId, String role) {
        callerSchoolScope.requireSameSchool(lesson.getCourse().getInstructor().getSchool().getId());
        if (lesson.isPublished()) {
            return;
        }
        if ("ADMIN".equals(role)) {
            adminSchoolScope.requireAccess(lesson.getCourse().getInstructor().getSchool().getId());
            return;
        }
        if ("INSTRUCTOR".equals(role) && lesson.getCourse().getInstructor().getUser().getId().equals(userId)) {
            return;
        }
        throw new ForbiddenException("You do not have access to this lesson");
    }
}
