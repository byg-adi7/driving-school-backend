package com.drivingschool.backend.quiz.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.learning.entity.Course;
import com.drivingschool.backend.quiz.entity.Quiz;
import org.springframework.stereotype.Component;

@Component
public class QuizValidator {

    public void validateCourseOwnership(Course course, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if ("INSTRUCTOR".equals(role) && course.getInstructor().getUser().getId().equals(userId)) {
            return;
        }
        throw new BadRequestException("You are not authorized to manage this course's quizzes");
    }

    public void validateQuizOwnership(Quiz quiz, Long userId, String role) {
        validateCourseOwnership(quiz.getCourse(), userId, role);
    }

    /**
     * Published quizzes are open to any authenticated role (mirrors the existing
     * unrestricted policy already used by getPublishedByCourse); unpublished/draft
     * quizzes are only visible to the owning instructor or ADMIN.
     */
    public void validateQuizReadAccess(Quiz quiz, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if (quiz.isPublished()) {
            return;
        }
        if ("INSTRUCTOR".equals(role) && quiz.getCourse().getInstructor().getUser().getId().equals(userId)) {
            return;
        }
        throw new BadRequestException("You do not have access to this quiz");
    }
}
