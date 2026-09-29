package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.notification.entity.Notification;

public interface NotificationSender {

    boolean supports(com.drivingschool.backend.notification.enums.NotificationChannel channel);

    void send(Notification notification);

    /**
     * True for a sender whose send() is @Async - it runs on another thread, which
     * can't see the notification row until the transaction that created it commits,
     * so it must only be handed the notification after that commit.
     */
    default boolean isAsynchronous() {
        return false;
    }
}
