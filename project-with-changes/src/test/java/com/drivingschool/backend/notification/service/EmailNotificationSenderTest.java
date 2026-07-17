package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.notification.entity.Notification;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.enums.NotificationStatus;
import com.drivingschool.backend.notification.repository.NotificationRepository;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailNotificationSenderTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private JavaMailSender mailSender;

    private EmailNotificationSender sender;

    @BeforeEach
    void setUp() {
        sender = new EmailNotificationSender(notificationRepository, mailSender, "no-reply@drivingschool.local");
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

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sentMessage = captor.getValue();
        assertThat(sentMessage.getTo()).containsExactly("student@example.com");
        assertThat(sentMessage.getSubject()).isEqualTo("Lesson scheduled");
        assertThat(sentMessage.getText()).isEqualTo("Be at the driving school at 10am");

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void send_whenMailSenderThrows_marksNotificationFailed() {
        Notification notification = Notification.builder()
                .user(User.builder().build())
                .subject("Lesson scheduled")
                .body("Be at the driving school at 10am")
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.PENDING)
                .recipientAddress("student@example.com")
                .build();
        doThrow(new MailSendException("smtp unreachable")).when(mailSender).send(any(SimpleMailMessage.class));

        sender.send(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        verify(notificationRepository).save(notification);
    }
}
