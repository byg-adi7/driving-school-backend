package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    NotificationResponse send(SendNotificationRequest request);

    Page<NotificationResponse> getMyNotifications(Pageable pageable);

    NotificationResponse markAsRead(Long notificationId);
}
