package com.drivingschool.backend.lesson.note.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonNoteResponse {

    private Long id;
    private Long bookingId;
    private Long instructorId;
    private String instructorName;
    private Long studentId;
    private String studentName;
    private String lessonSummary;
    private String strengths;
    private String weaknesses;
    private String recommendations;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedById;
    private String updatedByName;
}
