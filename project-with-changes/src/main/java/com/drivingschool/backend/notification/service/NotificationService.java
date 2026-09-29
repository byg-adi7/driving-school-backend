package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    NotificationResponse send(SendNotificationRequest request);

    /**
     * Best-effort notification about something the caller is doing in its own
     * transaction (a booking, a message, a new account...). Delivered only after that
     * transaction commits - never for a change that rolled back - in a transaction of
     * its own, and it never throws: a failed notification is logged and can't fail or
     * roll back the caller's work.
     */
    void sendAfterCommit(SendNotificationRequest request);

    /**
     * The POST /notifications/send entry point: same as send(), but first checks the
     * caller may target this recipient. send() itself stays unchecked because it's
     * also the internal system path (e.g. a regular admin's deletion request
     * notifying the bootstrap admin, who is outside that admin's school).
     */
    NotificationResponse sendAsCaller(SendNotificationRequest request);

    Page<NotificationResponse> getMyNotifications(Pageable pageable);

    NotificationResponse markAsRead(Long notificationId);
}
