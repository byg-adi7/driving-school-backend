package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.email.ResendEmailClient;
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
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailNotificationSenderTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private ResendEmailClient resendEmailClient;

    private EmailNotificationSender sender;

    @BeforeEach
    void setUp() {
        sender = new EmailNotificationSender(notificationRepository, resendEmailClient, "no-reply@drivingschool.local");
    }

    @Test
    void supports_emailChannel_returnsTrue() {
        assertThat(sender.supports(NotificationChannel.EMAIL)).isTrue();
        assertThat(sender.supports(NotificationChannel.SMS)).isFalse();
    }

    @Test
    void send_dispatchesMailAndMarksSent() {
        Notification notification = Notification.builder()
                .user(User.builder().build())
                .subject("Lesson scheduled")
                .body("Be at the driving school at 10am")
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.PENDING)
                .recipientAddress("student@example.com")
                .build();

        sender.send(notification);

        verify(resendEmailClient).send("no-reply@drivingschool.local", "student@example.com",
                "Lesson scheduled", "Be at the driving school at 10am");

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void send_whenResendClientThrows_marksNotificationFailed() {
        Notification notification = Notification.builder()
                .user(User.builder().build())
                .subject("Lesson scheduled")
                .body("Be at the driving school at 10am")
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.PENDING)
                .recipientAddress("student@example.com")
                .build();
        doThrow(new RestClientException("resend api unreachable"))
                .when(resendEmailClient).send("no-reply@drivingschool.local", "student@example.com",
                        "Lesson scheduled", "Be at the driving school at 10am");

        sender.send(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        verify(notificationRepository).save(notification);
    }
}
