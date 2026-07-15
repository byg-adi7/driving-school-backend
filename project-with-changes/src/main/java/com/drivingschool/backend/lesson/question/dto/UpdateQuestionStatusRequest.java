package com.drivingschool.backend.lesson.question.dto;

import com.drivingschool.backend.lesson.question.enums.QuestionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateQuestionStatusRequest {

    @NotNull(message = "New status is required")
    private QuestionStatus newStatus;

    private String changeReason;
}