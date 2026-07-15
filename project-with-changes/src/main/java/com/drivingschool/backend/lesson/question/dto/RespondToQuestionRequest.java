package com.drivingschool.backend.lesson.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RespondToQuestionRequest {

    @NotBlank(message = "Response is required")
    @Size(min = 10, max = 3000, message = "Response must be between 10 and 3000 characters")
    private String response;
}
