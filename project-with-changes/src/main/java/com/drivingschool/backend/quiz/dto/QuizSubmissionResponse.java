package com.drivingschool.backend.quiz.dto;

import com.drivingschool.backend.quiz.enums.SubmissionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class QuizSubmissionResponse {

    private final Long id;
    private final Long quizId;
    private final Long studentId;
    private final Integer attemptNumber;
    private final Integer score;
    private final boolean passed;
    private final SubmissionStatus status;
    private final LocalDateTime submittedAt;
}
