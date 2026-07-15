package com.drivingschool.backend.quiz.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class QuizResponse {

    private final Long id;
    private final Long courseId;
    private final String title;
    private final String description;
    private final Integer passingScore;
    private final Integer timeLimitMinutes;
    private final Integer maxAttempts;
    private final boolean published;
    private final List<QuizQuestionResponse> questions;
}
