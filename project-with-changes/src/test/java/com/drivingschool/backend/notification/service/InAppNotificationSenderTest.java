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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InAppNotificationSenderTest {

    @Mock private NotificationRepository notificationRepository;

    private InAppNotificationSender sender;

    @BeforeEach
    void setUp() {
        sender = new InAppNotificationSender(notificationRepository);
    }

    @Test
    void supports_inAppChannel_returnsTrue() {
        assertThat(sender.supports(NotificationChannel.IN_APP)).isTrue();
        assertThat(sender.supports(NotificationChannel.EMAIL)).isFalse();
    }

    @Test
    void send_marksNotificationSentAndSaves() {
        Notification notification = Notification.builder()
                .user(User.builder().build())
                .subject("Lesson scheduled")
                .body("Be at the driving school at 10am")
                .channel(NotificationChannel.IN_APP)
                .status(NotificationStatus.PENDING)
                .build();

        sender.send(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void send_whenSaveThrows_marksNotificationFailed() {
        Notification notification = Notification.builder()
                .user(User.builder().build())
                .subject("Lesson scheduled")
                .body("Be at the driving school at 10am")
                .channel(NotificationChannel.IN_APP)
                .status(NotificationStatus.PENDING)
                .build();
        org.mockito.Mockito.when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("db down"))
                .thenAnswer(inv -> inv.getArgument(0));

        sender.send(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }
}
