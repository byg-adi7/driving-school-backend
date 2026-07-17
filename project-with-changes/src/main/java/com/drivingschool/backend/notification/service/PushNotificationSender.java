package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.notification.entity.Notification;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PushNotificationSender implements NotificationSender {

    private final NotificationRepository notificationRepository;

    public PushNotificationSender(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.PUSH;
    }

    // No push provider (e.g. FCM/APNs) is integrated yet, so this channel cannot
    // actually deliver anything. Report FAILED rather than SENT so callers and
    // notification history don't get told a message went out when it didn't.
    @Override
    public void send(Notification notification) {
        log.warn("Push channel is not configured; notification {} to user {} was not delivered",
                notification.getId(), notification.getUser().getId());
        notification.markFailed("Push delivery is not configured");
        notificationRepository.save(notification);
    }
}
