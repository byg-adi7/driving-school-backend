package com.drivingschool.backend.progress.mapper;

import com.drivingschool.backend.progress.dto.DrivingAssessmentResponse;
import com.drivingschool.backend.progress.entity.DrivingAssessment;
import org.springframework.stereotype.Component;

@Component
public class DrivingAssessmentMapper {

    public DrivingAssessmentResponse toResponse(DrivingAssessment assessment) {
        return DrivingAssessmentResponse.builder()
                .id(assessment.getId())
                .studentId(assessment.getStudent().getId())
                .studentName(assessment.getStudent().getFirstName() + " " + assessment.getStudent().getLastName())
                .instructorId(assessment.getInstructor().getId())
                .instructorName(assessment.getInstructor().getFirstName() + " " + assessment.getInstructor().getLastName())
                .bookingId(assessment.getBooking() != null ? assessment.getBooking().getId() : null)
                .assessmentDate(assessment.getAssessmentDate())
                .score(assessment.getScore())
                .result(assessment.getResult())
                .feedback(assessment.getFeedback())
                .durationMinutes(assessment.getDurationMinutes())
                .createdAt(assessment.getCreatedAt())
                .updatedAt(assessment.getUpdatedAt())
                .build();
    }
}
