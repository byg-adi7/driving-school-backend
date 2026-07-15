package com.drivingschool.backend.quiz.dto;

import com.drivingschool.backend.quiz.enums.QuestionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class CreateQuizQuestionRequest {

    @NotBlank(message = "Question text is required")
    private final String questionText;

    @NotNull(message = "Question type is required")
    private final QuestionType questionType;

    private final String options;

    @NotBlank(message = "Correct answer is required")
    private final String correctAnswer;

    @NotNull(message = "Points is required")
    @Min(1)
    private final Integer points;

    @NotNull(message = "Question order is required")
    @Min(1)
    private final Integer questionOrder;
}
