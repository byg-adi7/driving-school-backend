package com.drivingschool.backend.progress.dto;

import com.drivingschool.backend.progress.enums.AssessmentResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class CreateDrivingAssessmentRequest {

    @NotNull(message = "Student ID is required")
    private Long studentId;

    /** Optional: which practical lesson this assessment was for. */
    private Long bookingId;

    @NotNull(message = "Assessment date is required")
    private LocalDateTime assessmentDate;

    @NotNull(message = "Score is required")
    @Min(value = 0, message = "Score must be at least 0")
    @Max(value = 100, message = "Score must be at most 100")
    private Integer score;

    @NotNull(message = "Result is required")
    private AssessmentResult result;

    @Size(max = 2000, message = "Feedback must be at most 2000 characters")
    private String feedback;

    @Min(value = 1, message = "Duration must be at least 1 minute")
    private Integer durationMinutes;
}
