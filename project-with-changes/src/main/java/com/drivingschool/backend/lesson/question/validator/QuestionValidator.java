package com.drivingschool.backend.lesson.question.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.lesson.question.dto.RespondToQuestionRequest;
import com.drivingschool.backend.lesson.question.dto.SubmitQuestionRequest;
import com.drivingschool.backend.lesson.question.entity.LessonQuestionSubmission;
import com.drivingschool.backend.lesson.question.enums.QuestionStatus;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class QuestionValidator {

    private final AdminSchoolScope adminSchoolScope;

    public QuestionValidator(AdminSchoolScope adminSchoolScope) {
        this.adminSchoolScope = adminSchoolScope;
    }

    public void validateSubmitRequest(SubmitQuestionRequest request) {
        if (request.getSubject() == null || request.getSubject().trim().isEmpty()) {
            throw new BadRequestException("Question subject is required");
        }
        if (request.getQuestionBody() == null || request.getQuestionBody().trim().isEmpty()) {
            throw new BadRequestException("Question body is required");
        }
    }

    public void validateRespondRequest(RespondToQuestionRequest request) {
        if (request.getResponse() == null || request.getResponse().trim().isEmpty()) {
            throw new BadRequestException("Response is required");
        }
    }

    public void validateInstructorAccess(LessonQuestionSubmission question, Long callerId) {
        if (question.getInstructor() != null && !question.getInstructor().getUser().getId().equals(callerId)) {
            throw new BadRequestException("You are not assigned to this question");
        }
    }

    public void validateStatusUpdate(LessonQuestionSubmission question, QuestionStatus newStatus, Long userId, String role) {
        if (!"ADMIN".equals(role) && !"INSTRUCTOR".equals(role)) {
            throw new BadRequestException("Only admins and instructors can update question status");
        }

        if ("INSTRUCTOR".equals(role) && question.getInstructor() != null
                && !question.getInstructor().getUser().getId().equals(userId)) {
            throw new BadRequestException("You are not assigned to this question");
        }

        if ("ADMIN".equals(role)) {
            adminSchoolScope.requireAccess(question.getStudent().getSchool().getId());
        }

        if (question.getStatus() == QuestionStatus.CLOSED) {
            throw new BadRequestException("Cannot change status of a closed question");
        }
    }

    /**
     * @return the one school an ADMIN-only listing must be filtered to for a regular
     *         admin, or empty for the bootstrap admin (every school).
     */
    public Optional<Long> adminSchoolFilter() {
        return adminSchoolScope.restrictedSchoolId();
    }

    public void validateReadAccess(LessonQuestionSubmission question, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            adminSchoolScope.requireAccess(question.getStudent().getSchool().getId());
            return;
        }
        if ("INSTRUCTOR".equals(role) && question.getInstructor() != null && question.getInstructor().getUser().getId().equals(userId)) {
            return;
        }
        if ("STUDENT".equals(role) && question.getStudent() != null && question.getStudent().getUser().getId().equals(userId)) {
            return;
        }
        throw new BadRequestException("You do not have access to this question");
    }
}
