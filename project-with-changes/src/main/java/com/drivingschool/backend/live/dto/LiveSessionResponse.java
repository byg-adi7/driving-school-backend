package com.drivingschool.backend.live.dto;

import com.drivingschool.backend.live.enums.SessionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LiveSessionResponse {

    private final Long id;
    private final String title;
    private final String description;
    private final LocalDateTime scheduledAt;
    private final Integer durationMinutes;
    // Null for a student who hasn't registered - registering reveals it.
    private final String meetingUrl;
    private final Integer maxParticipants;
    private final SessionStatus status;
    private final Long instructorId;
    private final String instructorName;
    private final Long schoolId;
    private final int registeredCount;
    // Whether the calling student is registered; absent for instructors and admins.
    private final Boolean registered;
    private final LocalDateTime endsAt;
}
