package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.notification.entity.Notification;

public interface NotificationSender {

    boolean supports(com.drivingschool.backend.notification.enums.NotificationChannel channel);

    void send(Notification notification);
}
