package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.entity.Notification;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.enums.NotificationStatus;
import com.drivingschool.backend.notification.mapper.NotificationMapper;
import com.drivingschool.backend.notification.repository.NotificationRepository;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
    @Mock private CurrentUserService currentUserService;
    private final NotificationMapper notificationMapper = new NotificationMapper();

    private NotificationServiceImpl service;

    private User userWithId(Long id, String email) {
        User user = User.builder().email(email).password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Notification notificationFor(User user, Long id) {
        Notification notification = Notification.builder()
                .user(user)
                .subject("Lesson reminder")
                .body("Your lesson is tomorrow")
                .channel(NotificationChannel.IN_APP)
                .status(NotificationStatus.SENT)
                .build();
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(emailSender.supports(NotificationChannel.EMAIL)).thenReturn(true);
        org.mockito.Mockito.lenient().when(smsSender.supports(NotificationChannel.EMAIL)).thenReturn(false);
        service = new NotificationServiceImpl(userRepository, notificationRepository,
                List.of(emailSender, smsSender), notificationMapper, currentUserService);
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
    void send_smsWithStudentPhoneOnFile_defaultsToStudentPhone() {
        User user = userWithId(1L, "student@example.com");
        StudentProfile profile = StudentProfile.builder().phone("+15550000000").build();
        ReflectionTestUtils.setField(user, "studentProfile", profile);
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId(1L).subject("Lesson reminder").body("Your lesson is tomorrow")
                .channel(NotificationChannel.SMS).build();

        when(smsSender.supports(NotificationChannel.SMS)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.send(request);

        org.mockito.ArgumentCaptor<Notification> captor = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getRecipientAddress()).isEqualTo("+15550000000");
    }

    @Test
    void send_smsWithNoPhoneOnFile_recipientAddressIsNull() {
        User user = userWithId(1L, "student@example.com");
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId(1L).subject("Lesson reminder").body("Your lesson is tomorrow")
                .channel(NotificationChannel.SMS).build();

        when(smsSender.supports(NotificationChannel.SMS)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.send(request);

        org.mockito.ArgumentCaptor<Notification> captor = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getRecipientAddress()).isNull();
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

    // --- getMyNotifications ---

    @Test
    void getMyNotifications_returnsCallersNotificationsPaged() {
        User user = userWithId(1L, "student@example.com");
        Notification notification = notificationFor(user, 10L);
        when(currentUserService.requireUserId()).thenReturn(1L);
        when(notificationRepository.findByUserIdAndChannelOrderByCreatedAtDesc(1L, NotificationChannel.IN_APP, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(notification)));

        Page<com.drivingschool.backend.notification.dto.NotificationResponse> result =
                service.getMyNotifications(Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(10L);
    }

    // --- markAsRead ---

    @Test
    void markAsRead_ownNotification_setsReadAt() {
        User user = userWithId(1L, "student@example.com");
        Notification notification = notificationFor(user, 10L);
        when(currentUserService.requireUserId()).thenReturn(1L);
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        var response = service.markAsRead(10L);

        assertThat(response.getReadAt()).isNotNull();
    }

    @Test
    void markAsRead_anotherUsersNotification_throwsBadRequestException() {
        User owner = userWithId(1L, "student@example.com");
        Notification notification = notificationFor(owner, 10L);
        when(currentUserService.requireUserId()).thenReturn(999L);
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.markAsRead(10L))
                .isInstanceOf(BadRequestException.class);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsRead_unknownNotification_throwsResourceNotFoundException() {
        when(currentUserService.requireUserId()).thenReturn(1L);
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
