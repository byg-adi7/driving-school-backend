package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.notification.entity.Notification;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SmsNotificationSender implements NotificationSender {

    private final NotificationRepository notificationRepository;

    public SmsNotificationSender(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.SMS;
    }

    // No SMS provider (e.g. Twilio) is integrated yet, so this channel cannot
    // actually deliver anything. Report FAILED rather than SENT so callers and
    // notification history don't get told a message went out when it didn't.
    @Override
    public void send(Notification notification) {
        log.warn("SMS channel is not configured; notification {} to {} was not delivered",
                notification.getId(), notification.getRecipientAddress());
        notification.markFailed("SMS delivery is not configured");
        notificationRepository.save(notification);
    }
}
