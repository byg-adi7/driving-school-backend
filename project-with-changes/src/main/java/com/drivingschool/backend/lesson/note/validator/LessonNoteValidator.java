package com.drivingschool.backend.lesson.note.validator;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.lesson.note.dto.CreateLessonNoteRequest;
import com.drivingschool.backend.lesson.note.entity.LessonNote;
import com.drivingschool.backend.student.entity.StudentProfile;
import org.springframework.stereotype.Component;

@Component
public class LessonNoteValidator {

    public void validateCreateRequest(CreateLessonNoteRequest request) {
        if (request.getLiveSessionId() == null || request.getLiveSessionId() <= 0) {
            throw new BadRequestException("Invalid live session ID");
        }
        if (request.getStudentId() == null || request.getStudentId() <= 0) {
            throw new BadRequestException("Invalid student ID");
        }
    }

    public void validateOwnership(LessonNote note, Long instructorId) {
        // instructorId is a user id; compare against the instructor profile's user id
        if (!note.getInstructor().getUser().getId().equals(instructorId)) {
            throw new BadRequestException("You are not authorized to modify this lesson note");
        }
    }

    public void validateReadAccess(LessonNote note, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if ("INSTRUCTOR".equals(role) && note.getInstructor().getUser().getId().equals(userId)) {
            return;
        }
        if ("STUDENT".equals(role) && note.getStudent().getUser().getId().equals(userId)) {
            return;
        }
        throw new BadRequestException("You do not have access to this lesson note");
    }

    /**
     * @param hasTaughtStudent true if the requesting instructor has authored at least
     *                         one lesson note for this student - the existing lesson-note
     *                         relationship is used as the "this instructor teaches this
     *                         student" signal, since there's no separate roster/assignment
     *                         concept in the data model.
     */
    public void validateStudentNotesAccess(StudentProfile student, Long userId, String role, boolean hasTaughtStudent) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if ("STUDENT".equals(role) && student.getUser().getId().equals(userId)) {
            return;
        }
        if ("INSTRUCTOR".equals(role) && hasTaughtStudent) {
            return;
        }
        throw new BadRequestException("You do not have access to this student's lesson notes");
    }

    public void validateInstructorNotesAccess(InstructorProfile instructor, Long userId, String role) {
        if ("ADMIN".equals(role)) {
            return;
        }
        if ("INSTRUCTOR".equals(role) && instructor.getUser().getId().equals(userId)) {
            return;
        }
        throw new BadRequestException("You do not have access to this instructor's lesson notes");
    }
}
