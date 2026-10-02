package com.drivingschool.backend.attendance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.drivingschool.backend.attendance.enums.LessonType;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** The phone's location fix - straight from the browser's Geolocation API (coords.*). */
@Getter
@Builder
@Jacksonized
public class CheckInRequest {

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private final Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private final Double longitude;

    @NotNull(message = "Accuracy is required")
    @Positive(message = "Accuracy must be positive")
    private final Double accuracyMeters;

    /** Required for students: today's lesson is PRACTICAL or THEORY. */
    private final LessonType lessonType;

    /** Optional, e.g. "Reverse parking" or "Road signs". */
    @Size(max = 200, message = "Topic must not exceed 200 characters")
    private final String topic;
}
