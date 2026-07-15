package com.drivingschool.backend.live.dto;

import com.drivingschool.backend.live.enums.SessionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class LiveSessionResponse {

    private final Long id;
    private final String title;
    private final String description;
    private final LocalDateTime scheduledAt;
    private final Integer durationMinutes;
    private final String meetingUrl;
    private final Integer maxParticipants;
    private final SessionStatus status;
    private final Long instructorId;
    private final String instructorName;
    private final Long schoolId;
    private final int registeredCount;
}
