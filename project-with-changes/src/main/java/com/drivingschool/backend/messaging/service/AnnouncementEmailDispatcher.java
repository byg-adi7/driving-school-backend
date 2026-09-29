package com.drivingschool.backend.messaging.service;

import com.drivingschool.backend.email.ResendEmailClient;
import com.drivingschool.backend.notification.entity.Notification;
import com.drivingschool.backend.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Emails one announcement to every student of a school through Resend's batch
 * endpoint - one request per 100 recipients, sent one after another on a single
 * background thread. Going through EmailNotificationSender instead would fire one
 * request per student in parallel (up to 10 executor threads), which runs straight
 * into Resend's default rate limit of 10 requests/second at a normal school size.
 *
 * Each student still has their own EMAIL Notification row (created PENDING by
 * AnnouncementServiceImpl), updated to SENT or FAILED per batch, so delivery stays
 * visible per recipient exactly as for any other email.
 */
@Slf4j
@Component
public class AnnouncementEmailDispatcher {

    private final NotificationRepository notificationRepository;
    private final ResendEmailClient resendEmailClient;
    private final String fromAddress;

    public AnnouncementEmailDispatcher(NotificationRepository notificationRepository,
                                       ResendEmailClient resendEmailClient,
                                       @Value("${app.mail.from}") String fromAddress) {
        this.notificationRepository = notificationRepository;
        this.resendEmailClient = resendEmailClient;
        this.fromAddress = fromAddress;
    }

    /** Called after the announcement's transaction commits, so every row is visible here. */
    @Async("notificationExecutor")
    public void dispatch(List<Long> notificationIds) {
        List<Notification> notifications = notificationRepository.findAllById(notificationIds);
        for (int start = 0; start < notifications.size(); start += ResendEmailClient.MAX_BATCH_SIZE) {
            List<Notification> batch = notifications.subList(start,
                    Math.min(start + ResendEmailClient.MAX_BATCH_SIZE, notifications.size()));
            try {
                resendEmailClient.sendBatch(fromAddress, batch.stream()
                        .map(n -> new ResendEmailClient.BatchEmail(n.getRecipientAddress(), n.getSubject(), n.getBody()))
                        .toList());
                batch.forEach(Notification::markSent);
            } catch (Exception ex) {
                log.error("Failed to send announcement email batch of {} (first notification id {})",
                        batch.size(), batch.get(0).getId(), ex);
                batch.forEach(n -> n.markFailed(ex.getMessage()));
            }
            notificationRepository.saveAll(batch);
        }
    }
}
