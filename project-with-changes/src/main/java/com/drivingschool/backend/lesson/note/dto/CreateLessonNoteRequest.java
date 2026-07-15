package com.drivingschool.backend.lesson.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateLessonNoteRequest {

    @NotNull(message = "Live session ID is required")
    private Long liveSessionId;

    @NotNull(message = "Student ID is required")
    private Long studentId;

    @NotBlank(message = "Lesson summary is required")
    @Size(min = 10, max = 1000, message = "Lesson summary must be between 10 and 1000 characters")
    private String lessonSummary;

    @NotBlank(message = "Strengths are required")
    @Size(min = 10, max = 1500, message = "Strengths must be between 10 and 1500 characters")
    private String strengths;

    @NotBlank(message = "Weaknesses are required")
    @Size(min = 10, max = 1500, message = "Weaknesses must be between 10 and 1500 characters")
    private String weaknesses;

    @NotBlank(message = "Recommendations are required")
    @Size(min = 10, max = 1500, message = "Recommendations must be between 10 and 1500 characters")
    private String recommendations;
}