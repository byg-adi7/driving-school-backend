package com.drivingschool.backend.lesson.question.dto;

import com.drivingschool.backend.lesson.question.enums.QuestionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusHistoryResponse {

    private Long id;
    private Long questionSubmissionId;
    private QuestionStatus previousStatus;
    private QuestionStatus newStatus;
    private Long changedById;
    private String changedByName;
    private LocalDateTime createdAt;
    private String changeReason;
}
