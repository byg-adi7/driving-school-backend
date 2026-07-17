package com.drivingschool.backend.learning.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateVideoLessonRequest {

    @Size(max = 200)
    private String title;

    @Size(max = 2000)
    private String description;

    @Size(max = 500)
    private String videoUrl;

    @Min(1)
    private Integer lessonOrder;

    @Min(0)
    private Integer durationSeconds;
}
