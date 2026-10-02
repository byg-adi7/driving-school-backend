package com.drivingschool.backend.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
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
    // Only for a STUDENT caller of GET /quizzes/{id}: their own attempts so far.
    private final MyAttempts myAttempts;

    @Getter
    @Builder
    public static class MyAttempts {
        private final int attemptsUsed;
        private final int attemptsRemaining;
        // Null until the first attempt.
        private final Integer bestScore;
        private final boolean passed;
    }
}
