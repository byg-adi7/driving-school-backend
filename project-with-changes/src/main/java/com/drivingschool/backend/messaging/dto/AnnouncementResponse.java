package com.drivingschool.backend.messaging.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AnnouncementResponse {

    private final Long id;
    private final Long schoolId;
    private final Long instructorProfileId;
    private final String instructorName;
    private final String subject;
    private final String body;
    private final LocalDateTime createdAt;
    // Only set on the create response: how many students it was sent to.
    private final Integer recipientCount;
}
