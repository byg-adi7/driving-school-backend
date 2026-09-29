package com.drivingschool.backend.quiz.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.learning.entity.Course;
import com.drivingschool.backend.quiz.entity.Quiz;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.school.validator.CallerSchoolScope;
import com.drivingschool.backend.student.entity.StudentProfile;
import org.springframework.stereotype.Component;

@Component
public class QuizValidator {

    private final AdminSchoolScope adminSchoolScope;
    private final CallerSchoolScope callerSchoolScope;

    public QuizValidator(AdminSchoolScope adminSchoolScope, CallerSchoolScope callerSchoolScope) {
        this.adminSchoolScope = adminSchoolScope;
        this.callerSchoolScope = callerSchoolScope;
    }

    /** A course's quizzes are only visible within the course's own school (bootstrap admin: any). */
    public void validateCourseSchoolAccess(Course course) {
        callerSchoolScope.requireSameSchool(course.getInstructor().getSchool().getId());
    }

    /** An ADMIN submitting on a student's behalf must share that student's school. */
    public void validateAdminSchoolAccess(StudentProfile student) {
        adminSchoolScope.requireAccess(student.getSchool().getId());
    }

    public void validateCourseOwnership(Course course, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            adminSchoolScope.requireAccess(course.getInstructor().getSchool().getId());
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
        validateCourseSchoolAccess(quiz.getCourse());
        if (quiz.isPublished()) {
            return;
        }
        if ("ADMIN".equals(role)) {
            adminSchoolScope.requireAccess(quiz.getCourse().getInstructor().getSchool().getId());
            return;
        }
        if ("INSTRUCTOR".equals(role) && quiz.getCourse().getInstructor().getUser().getId().equals(userId)) {
            return;
        }
        throw new BadRequestException("You do not have access to this quiz");
    }
}
