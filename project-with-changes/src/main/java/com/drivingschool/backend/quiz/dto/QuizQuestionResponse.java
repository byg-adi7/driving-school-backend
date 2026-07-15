package com.drivingschool.backend.quiz.dto;

import com.drivingschool.backend.quiz.enums.QuestionType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuizQuestionResponse {

    private final Long id;
    private final String questionText;
    private final QuestionType questionType;
    private final String options;
    private final Integer points;
    private final Integer questionOrder;
}
