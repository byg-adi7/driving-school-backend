package com.drivingschool.backend.lesson.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SubmitQuestionRequest {

    @NotBlank(message = "Subject is required")
    @Size(min = 5, max = 255, message = "Subject must be between 5 and 255 characters")
    private String subject;

    @NotBlank(message = "Question body is required")
    @Size(min = 10, max = 3000, message = "Question must be between 10 and 3000 characters")
    private String questionBody;

    /** InstructorProfile.id */
    private Long assignedInstructorId;
}
