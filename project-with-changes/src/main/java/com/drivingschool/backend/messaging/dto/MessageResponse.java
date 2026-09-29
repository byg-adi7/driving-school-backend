package com.drivingschool.backend.messaging.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MessageResponse {

    private final Long id;
    private final Long conversationId;
    private final Long senderUserId;
    // true when the caller sent it - lets a chat view align bubbles without comparing ids
    private final boolean mine;
    private final String body;
    private final LocalDateTime sentAt;
    private final LocalDateTime readAt;
}
