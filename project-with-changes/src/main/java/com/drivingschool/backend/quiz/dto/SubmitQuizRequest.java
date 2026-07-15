package com.drivingschool.backend.quiz.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

@Getter
@Builder
@Jacksonized
public class SubmitQuizRequest {

    @NotNull(message = "Student ID is required")
    private final Long studentId;

    @NotEmpty(message = "Answers are required")
    private final Map<Long, String> answers;
}
