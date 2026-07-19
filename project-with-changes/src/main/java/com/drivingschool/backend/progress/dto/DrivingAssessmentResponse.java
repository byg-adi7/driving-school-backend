package com.drivingschool.backend.progress.dto;

import com.drivingschool.backend.progress.enums.AssessmentResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrivingAssessmentResponse {

    private Long id;
    private Long studentId;
    private String studentName;
    private Long instructorId;
    private String instructorName;
    private Long bookingId;
    private LocalDateTime assessmentDate;
    private Integer score;
    private AssessmentResult result;
    private String feedback;
    private Integer durationMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
