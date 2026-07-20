package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.config.TwilioConfig;
import com.drivingschool.backend.notification.entity.Notification;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.repository.NotificationRepository;
import com.drivingschool.backend.sms.TwilioSmsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmsNotificationSender implements NotificationSender {

    private final NotificationRepository notificationRepository;
    private final TwilioConfig twilioConfig;
    private final TwilioSmsClient twilioSmsClient;

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.SMS;
    }

    // Until TWILIO_ACCOUNT_SID/TWILIO_AUTH_TOKEN/TWILIO_FROM_NUMBER are all set
    // in the environment, this stays a safe no-op: FAILED rather than SENT, so
    // callers and notification history don't get told a message went out when
    // it didn't. Once those variables are set, real delivery kicks in with no
    // code change needed.
    @Override
    @Async("notificationExecutor")
    public void send(Notification notification) {
        if (!twilioConfig.isConfigured()) {
            log.warn("SMS channel is not configured; notification {} to {} was not delivered",
                    notification.getId(), notification.getRecipientAddress());
            notification.markFailed("SMS delivery is not configured");
            notificationRepository.save(notification);
            return;
        }

        String toNumber = notification.getRecipientAddress();
        if (toNumber == null || toNumber.isBlank()) {
            log.warn("No phone number on file for notification {} (user {})",
                    notification.getId(), notification.getUser().getId());
            notification.markFailed("No phone number on file");
            notificationRepository.save(notification);
            return;
        }

        try {
            twilioSmsClient.send(toNumber, notification.getBody());
            notification.markSent();
            notificationRepository.save(notification);
        } catch (Exception ex) {
            log.error("Failed to send SMS notification {}", notification.getId(), ex);
            notification.markFailed(ex.getMessage());
            notificationRepository.save(notification);
        }
    }
}
