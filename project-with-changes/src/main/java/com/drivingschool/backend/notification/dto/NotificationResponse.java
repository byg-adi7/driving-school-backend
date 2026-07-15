package com.drivingschool.backend.notification.dto;

import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.enums.NotificationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationResponse {

    private final Long id;
    private final Long userId;
    private final String subject;
    private final NotificationChannel channel;
    private final NotificationStatus status;
    private final LocalDateTime sentAt;
}
