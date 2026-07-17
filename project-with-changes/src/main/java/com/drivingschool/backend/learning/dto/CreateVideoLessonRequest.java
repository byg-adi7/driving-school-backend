package com.drivingschool.backend.learning.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class CreateVideoLessonRequest {

    @NotNull(message = "Course ID is required")
    private final Long courseId;

    @NotBlank(message = "Title is required")
    @Size(max = 200)
    private final String title;

    @Size(max = 2000)
    private final String description;

    @NotBlank(message = "Video URL is required")
    @Size(max = 500)
    private final String videoUrl;

    @NotNull(message = "Lesson order is required")
    @Min(1)
    private final Integer lessonOrder;

    @Min(0)
    private final Integer durationSeconds;
}
