package com.drivingschool.backend.progress.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.progress.dto.CreateDrivingAssessmentRequest;
import com.drivingschool.backend.progress.entity.DrivingAssessment;
import com.drivingschool.backend.student.entity.StudentProfile;
import org.springframework.stereotype.Component;

@Component
public class DrivingAssessmentValidator {

    public void validateCreateRequest(CreateDrivingAssessmentRequest request) {
        if (request.getStudentId() == null || request.getStudentId() <= 0) {
            throw new BadRequestException("Invalid student ID");
        }
    }

    public void validateOwnership(DrivingAssessment assessment, Long callerId) {
        if (!assessment.getInstructor().getUser().getId().equals(callerId)) {
            throw new BadRequestException("You are not authorized to modify this driving assessment");
        }
    }

    public void validateReadAccess(DrivingAssessment assessment, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if ("INSTRUCTOR".equals(role) && assessment.getInstructor().getUser().getId().equals(userId)) {
            return;
        }
        if ("STUDENT".equals(role) && assessment.getStudent().getUser().getId().equals(userId)) {
            return;
        }
        throw new BadRequestException("You do not have access to this driving assessment");
    }

    /**
     * @param hasTaughtStudent true if the requesting instructor has authored at least one
     *                         driving assessment for this student - the existing assessment
     *                         relationship is used as the "this instructor teaches this
     *                         student" signal, since there's no separate roster/assignment
     *                         concept in the data model (same idiom as LessonNoteValidator).
     */
    public void validateStudentAssessmentsAccess(StudentProfile student, Long userId, String role, boolean hasTaughtStudent) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if ("STUDENT".equals(role) && student.getUser().getId().equals(userId)) {
            return;
        }
        if ("INSTRUCTOR".equals(role) && hasTaughtStudent) {
            return;
        }
        throw new BadRequestException("You do not have access to this student's driving assessments");
    }

    public void validateInstructorAssessmentsAccess(InstructorProfile instructor, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if ("INSTRUCTOR".equals(role) && instructor.getUser().getId().equals(userId)) {
            return;
        }
        throw new BadRequestException("You do not have access to this instructor's driving assessments");
    }
}
