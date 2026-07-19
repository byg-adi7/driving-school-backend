package com.drivingschool.backend.progress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateDrivingAssessmentFeedbackRequest {

    @NotBlank(message = "Feedback is required")
    @Size(max = 2000, message = "Feedback must be at most 2000 characters")
    private String feedback;
}
