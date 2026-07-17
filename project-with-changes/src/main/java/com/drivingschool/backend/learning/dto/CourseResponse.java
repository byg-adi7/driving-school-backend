package com.drivingschool.backend.learning.dto;

import com.drivingschool.backend.learning.enums.CourseStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CourseResponse {

    private final Long id;
    private final Long instructorId;
    private final String title;
    private final String description;
    private final CourseStatus status;
    private final LocalDateTime publishedAt;
}
