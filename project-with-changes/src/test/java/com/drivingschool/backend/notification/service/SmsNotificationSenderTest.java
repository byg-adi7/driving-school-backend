package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.config.TwilioConfig;
import com.drivingschool.backend.notification.entity.Notification;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.enums.NotificationStatus;
import com.drivingschool.backend.notification.repository.NotificationRepository;
import com.drivingschool.backend.sms.TwilioSmsClient;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsNotificationSenderTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private TwilioConfig twilioConfig;
    @Mock private TwilioSmsClient twilioSmsClient;

    private SmsNotificationSender sender;

    @BeforeEach
    void setUp() {
        sender = new SmsNotificationSender(notificationRepository, twilioConfig, twilioSmsClient);
    }

    private Notification notificationWithAddress(String recipientAddress) {
        User user = User.builder().build();
        Notification notification = Notification.builder()
                .user(user)
                .subject("Lesson reminder")
                .body("Your lesson starts in 1 hour")
                .channel(NotificationChannel.SMS)
                .status(NotificationStatus.PENDING)
                .recipientAddress(recipientAddress)
                .build();
        ReflectionTestUtils.setField(notification, "id", 1L);
        return notification;
    }

    @Test
    void supports_smsChannel_returnsTrue() {
        assertThat(sender.supports(NotificationChannel.SMS)).isTrue();
        assertThat(sender.supports(NotificationChannel.EMAIL)).isFalse();
    }

    @Test
    void send_withNoProviderConfigured_marksNotificationFailedRatherThanSent() {
        when(twilioConfig.isConfigured()).thenReturn(false);
        Notification notification = notificationWithAddress("+15550000000");

        sender.send(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getFailureReason()).isEqualTo("SMS delivery is not configured");
        verify(notificationRepository).save(notification);
    }

    @Test
    void send_configuredButNoPhoneNumberOnFile_marksNotificationFailed() {
        when(twilioConfig.isConfigured()).thenReturn(true);
        Notification notification = notificationWithAddress(null);

        sender.send(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getFailureReason()).isEqualTo("No phone number on file");
        verify(notificationRepository).save(notification);
    }

    @Test
    void send_configuredWithPhoneNumber_sendsAndMarksSent() {
        when(twilioConfig.isConfigured()).thenReturn(true);
        Notification notification = notificationWithAddress("+15550000000");

        sender.send(notification);

        verify(twilioSmsClient).send("+15550000000", "Your lesson starts in 1 hour");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(notificationRepository).save(notification);
    }

    @Test
    void send_providerThrows_marksNotificationFailedWithReason() {
        when(twilioConfig.isConfigured()).thenReturn(true);
        Notification notification = notificationWithAddress("+15550000000");
        doThrow(new RuntimeException("Twilio 400: invalid number"))
                .when(twilioSmsClient).send("+15550000000", "Your lesson starts in 1 hour");

        sender.send(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getFailureReason()).isEqualTo("Twilio 400: invalid number");
        verify(notificationRepository).save(notification);
    }

    @Test
    void isAsynchronous_matchesHowSendRuns() {
        // NotificationServiceImpl defers async senders until after commit - its send() is @Async.
        assertThat(sender.isAsynchronous()).isTrue();
    }
}
