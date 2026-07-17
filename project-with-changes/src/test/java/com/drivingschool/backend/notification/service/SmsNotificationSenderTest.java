package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.notification.entity.Notification;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.enums.NotificationStatus;
import com.drivingschool.backend.notification.repository.NotificationRepository;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SmsNotificationSenderTest {

    @Mock private NotificationRepository notificationRepository;

    private SmsNotificationSender sender;

    @BeforeEach
    void setUp() {
        sender = new SmsNotificationSender(notificationRepository);
    }

    @Test
    void supports_smsChannel_returnsTrue() {
        assertThat(sender.supports(NotificationChannel.SMS)).isTrue();
        assertThat(sender.supports(NotificationChannel.EMAIL)).isFalse();
    }

    @Test
    void send_withNoProviderConfigured_marksNotificationFailedRatherThanSent() {
        Notification notification = Notification.builder()
                .user(User.builder().build())
                .subject("Lesson reminder")
                .body("Your lesson starts in 1 hour")
                .channel(NotificationChannel.SMS)
                .status(NotificationStatus.PENDING)
                .recipientAddress("+15550000000")
                .build();

        sender.send(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getFailureReason()).isEqualTo("SMS delivery is not configured");
        verify(notificationRepository).save(notification);
    }
}
