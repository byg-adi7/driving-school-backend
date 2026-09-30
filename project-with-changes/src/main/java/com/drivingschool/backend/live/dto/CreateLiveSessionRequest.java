package com.drivingschool.backend.live.dto;

import com.drivingschool.backend.live.entity.LiveSession;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

@Getter
@Builder
@Jacksonized
public class CreateLiveSessionRequest {

    @NotNull(message = "Instructor ID is required")
    private final Long instructorId;

    @NotNull(message = "School ID is required")
    private final Long schoolId;

    @NotBlank(message = "Title is required")
    @Size(max = 200)
    private final String title;

    @Size(max = 2000)
    private final String description;

    @NotNull(message = "Scheduled time is required")
    @Future(message = "Scheduled time must be in the future")
    private final LocalDateTime scheduledAt;

    @NotNull(message = "Duration is required")
    @Positive(message = "Duration must be positive")
    @Max(value = LiveSession.MAX_DURATION_MINUTES, message = "Duration must be at most 24 hours")
    private final Integer durationMinutes;

    @Size(max = 500)
    private final String meetingUrl;

    @Positive(message = "Max participants must be positive")
    private final Integer maxParticipants;
}
