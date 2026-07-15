package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.dto.NotificationResponse;

public interface NotificationService {

    NotificationResponse send(SendNotificationRequest request);
}
