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
public class QuestionResponse {

    private Long id;
    private Long studentId;
    private String studentName;
    private Long instructorId;
    private String instructorName;
    private String subject;
    private String questionBody;
    private String response;
    private QuestionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long respondedById;
    private String respondedByName;
    private LocalDateTime respondedAt;
}
