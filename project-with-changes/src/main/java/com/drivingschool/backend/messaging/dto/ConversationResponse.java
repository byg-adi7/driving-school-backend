package com.drivingschool.backend.messaging.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** One row of the caller's inbox - "counterpart" is always the OTHER participant. */
@Getter
@Builder
public class ConversationResponse {

    private final Long id;
    private final Long studentProfileId;
    private final Long instructorProfileId;
    private final String counterpartName;
    private final String counterpartRole;
    private final String lastMessagePreview;
    private final LocalDateTime lastMessageAt;
    private final long unreadCount;
}
