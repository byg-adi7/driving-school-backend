package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.entity.Notification;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.enums.NotificationStatus;
import com.drivingschool.backend.notification.mapper.NotificationMapper;
import com.drivingschool.backend.notification.repository.NotificationRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationSender emailSender;
    @Mock private NotificationSender smsSender;
    private final NotificationMapper notificationMapper = new NotificationMapper();

    private NotificationServiceImpl service;

    private User userWithId(Long id, String email) {
        User user = User.builder().email(email).password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(emailSender.supports(NotificationChannel.EMAIL)).thenReturn(true);
        org.mockito.Mockito.lenient().when(smsSender.supports(NotificationChannel.EMAIL)).thenReturn(false);
        service = new NotificationServiceImpl(userRepository, notificationRepository,
                List.of(emailSender, smsSender), notificationMapper);
    }

    @Test
    void send_withMatchingSender_dispatchesAndSavesNotification() {
        User user = userWithId(1L, "student@example.com");
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId(1L).subject("Lesson reminder").body("Your lesson is tomorrow")
                .channel(NotificationChannel.EMAIL).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.findById(any())).thenReturn(Optional.empty());

        service.send(request);

        verify(emailSender).send(any(Notification.class));
        verify(smsSender, never()).send(any());
    }

    @Test
    void send_recipientAddressNotProvided_defaultsToUserEmail() {
        User user = userWithId(1L, "student@example.com");
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId(1L).subject("Lesson reminder").body("Your lesson is tomorrow")
                .channel(NotificationChannel.EMAIL).recipientAddress(null).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.send(request);

        org.mockito.ArgumentCaptor<Notification> captor = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getRecipientAddress()).isEqualTo("student@example.com");
    }

    @Test
    void send_unknownUser_throwsResourceNotFoundException() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId(999L).subject("x").body("y").channel(NotificationChannel.EMAIL).build();
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.send(request)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void send_noSenderSupportsChannel_marksNotificationFailed() {
        User user = userWithId(1L, "student@example.com");
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId(1L).subject("x").body("y").channel(NotificationChannel.PUSH).build();

        when(emailSender.supports(NotificationChannel.PUSH)).thenReturn(false);
        when(smsSender.supports(NotificationChannel.PUSH)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.send(request);

        org.mockito.ArgumentCaptor<Notification> captor = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
    }
}
