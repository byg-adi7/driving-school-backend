package com.drivingschool.backend.notification.mapper;

import com.drivingschool.backend.notification.dto.NotificationResponse;
import com.drivingschool.backend.notification.entity.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUser().getId())
                .subject(notification.getSubject())
                .channel(notification.getChannel())
                .status(notification.getStatus())
                .sentAt(notification.getSentAt())
                .build();
    }
}
