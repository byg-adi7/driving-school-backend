package com.drivingschool.backend.learning.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.learning.entity.Course;
import com.drivingschool.backend.learning.entity.VideoLesson;
import com.drivingschool.backend.learning.enums.CourseStatus;
import org.springframework.stereotype.Component;

@Component
public class LearningValidator {

    public void validateCourseOwnership(Course course, Long userId, String role) {
        if (!isCourseOwnerOrAdmin(course, userId, role)) {
            throw new BadRequestException("You are not authorized to manage this course");
        }
    }

    public boolean isCourseOwnerOrAdmin(Course course, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            return true;
        }
        return "INSTRUCTOR".equals(role) && course.getInstructor().getUser().getId().equals(userId);
    }

    /**
     * Published courses are open to any authenticated role; draft/archived
     * courses are only visible to the owning instructor or ADMIN.
     */
    public void validateCourseReadAccess(Course course, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if (course.getStatus() == CourseStatus.PUBLISHED) {
            return;
        }
        if ("INSTRUCTOR".equals(role) && course.getInstructor().getUser().getId().equals(userId)) {
            return;
        }
        throw new BadRequestException("You do not have access to this course");
    }

    public void validateLessonOwnership(VideoLesson lesson, Long userId, String role) {
        validateCourseOwnership(lesson.getCourse(), userId, role);
    }

    /**
     * Published lessons are open to any authenticated role (mirrors the quiz
     * module's equivalent policy); unpublished lessons are only visible to the
     * owning instructor or ADMIN.
     */
    public void validateLessonReadAccess(VideoLesson lesson, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if (lesson.isPublished()) {
            return;
        }
        if ("INSTRUCTOR".equals(role) && lesson.getCourse().getInstructor().getUser().getId().equals(userId)) {
            return;
        }
        throw new BadRequestException("You do not have access to this lesson");
    }
}
