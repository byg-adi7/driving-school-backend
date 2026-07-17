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
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PushNotificationSenderTest {

    @Mock private NotificationRepository notificationRepository;

    private PushNotificationSender sender;

    @BeforeEach
    void setUp() {
        sender = new PushNotificationSender(notificationRepository);
    }

    @Test
    void supports_pushChannel_returnsTrue() {
        assertThat(sender.supports(NotificationChannel.PUSH)).isTrue();
        assertThat(sender.supports(NotificationChannel.EMAIL)).isFalse();
    }

    @Test
    void send_withNoProviderConfigured_marksNotificationFailedRatherThanSent() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", 1L);
        Notification notification = Notification.builder()
                .user(user)
                .subject("Lesson reminder")
                .body("Your lesson starts in 1 hour")
                .channel(NotificationChannel.PUSH)
                .status(NotificationStatus.PENDING)
                .build();

        sender.send(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getFailureReason()).isEqualTo("Push delivery is not configured");
        verify(notificationRepository).save(notification);
    }
}
