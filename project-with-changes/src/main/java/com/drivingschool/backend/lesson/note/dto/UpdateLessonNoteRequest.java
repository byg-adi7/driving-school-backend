package com.drivingschool.backend.lesson.note.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateLessonNoteRequest {

    @Size(min = 10, max = 1000, message = "Lesson summary must be between 10 and 1000 characters")
    private String lessonSummary;

    @Size(min = 10, max = 1500, message = "Strengths must be between 10 and 1500 characters")
    private String strengths;

    @Size(min = 10, max = 1500, message = "Weaknesses must be between 10 and 1500 characters")
    private String weaknesses;

    @Size(min = 10, max = 1500, message = "Recommendations must be between 10 and 1500 characters")
    private String recommendations;
}

