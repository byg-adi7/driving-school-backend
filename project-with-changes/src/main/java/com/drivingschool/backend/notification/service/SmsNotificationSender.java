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

    @Override
    public void send(Notification notification) {
        try {
            log.info("Sending SMS to {}: {}", notification.getRecipientAddress(), notification.getSubject());
            notification.markSent();
            notificationRepository.save(notification);
        } catch (Exception ex) {
            log.error("Failed to send SMS notification {}", notification.getId(), ex);
            notification.markFailed(ex.getMessage());
            notificationRepository.save(notification);
        }
    }
}
