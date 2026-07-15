package com.drivingschool.backend.quiz.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class CreateQuizRequest {

    @NotNull(message = "Course ID is required")
    private final Long courseId;

    @NotBlank(message = "Title is required")
    @Size(max = 200)
    private final String title;

    @Size(max = 2000)
    private final String description;

    @NotNull(message = "Passing score is required")
    @Min(0)
    @Max(100)
    private final Integer passingScore;

    @Min(1)
    private final Integer timeLimitMinutes;

    @NotNull(message = "Max attempts is required")
    @Min(1)
    private final Integer maxAttempts;
}
