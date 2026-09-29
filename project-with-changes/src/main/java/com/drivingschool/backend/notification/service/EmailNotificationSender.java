package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.email.ResendEmailClient;
import com.drivingschool.backend.notification.entity.Notification;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailNotificationSender implements NotificationSender {

    private final NotificationRepository notificationRepository;
    private final ResendEmailClient resendEmailClient;
    private final String fromAddress;

    public EmailNotificationSender(NotificationRepository notificationRepository,
                                    ResendEmailClient resendEmailClient,
                                    @Value("${app.mail.from}") String fromAddress) {
        this.notificationRepository = notificationRepository;
        this.resendEmailClient = resendEmailClient;
        this.fromAddress = fromAddress;
    }

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.EMAIL;
    }

    // Runs off the request thread - callers (NotificationServiceImpl.send(),
    // and transitively POST /notifications/send) get their response back with
    // the notification still in PENDING status; the SENT/FAILED update lands
    // moments later via the save() calls below. That's the intended tradeoff:
    // a slow/unreachable mail server no longer adds latency to the caller.
    @Override
    public boolean isAsynchronous() {
        return true;
    }

    @Override
    @Async("notificationExecutor")
    public void send(Notification notification) {
        try {
            resendEmailClient.send(fromAddress, notification.getRecipientAddress(),
                    notification.getSubject(), notification.getBody());
            notification.markSent();
            notificationRepository.save(notification);
        } catch (Exception ex) {
            log.error("Failed to send email notification {}", notification.getId(), ex);
            notification.markFailed(ex.getMessage());
            notificationRepository.save(notification);
        }
    }
}
