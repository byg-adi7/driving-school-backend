package com.drivingschool.backend.learning.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VideoLessonResponse {

    private final Long id;
    private final Long courseId;
    private final String title;
    private final String description;
    private final String videoUrl;
    private final Integer lessonOrder;
    private final Integer durationSeconds;
    private final boolean published;
}
