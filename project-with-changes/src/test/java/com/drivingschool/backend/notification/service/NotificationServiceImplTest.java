package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.entity.Notification;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.enums.NotificationStatus;
import com.drivingschool.backend.notification.mapper.NotificationMapper;
import com.drivingschool.backend.notification.repository.NotificationRepository;
import com.drivingschool.backend.realtime.RealtimeEvent;
import com.drivingschool.backend.realtime.RealtimePublisher;
import com.drivingschool.backend.school.validator.CallerSchoolScope;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    @Mock private CallerSchoolScope callerSchoolScope;
    @Mock private RealtimePublisher realtimePublisher;
    @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;
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
                List.of(emailSender, smsSender), notificationMapper, currentUserService, callerSchoolScope,
                realtimePublisher, transactionManager);
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

    @Test
    void sendAsCaller_recipientOutsideCallersSchool_isRejectedBeforeAnythingIsSaved() {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .userId(1L).subject("x").body("y").channel(NotificationChannel.EMAIL).build();
        doThrow(new BadRequestException("no access")).when(callerSchoolScope).requireSameSchoolAsUser(1L);

        assertThatThrownBy(() -> service.sendAsCaller(request)).isInstanceOf(BadRequestException.class);
        verify(notificationRepository, never()).save(any());
    }

    // --- async senders are only handed a notification once its transaction commits ---

    private SendNotificationRequest emailRequest() {
        return SendNotificationRequest.builder()
                .userId(1L).subject("Lesson reminder").body("Your lesson is tomorrow")
                .channel(NotificationChannel.EMAIL).build();
    }

    @Test
    void send_asyncSenderInsideATransaction_isDeferredUntilAfterCommit() {
        when(emailSender.isAsynchronous()).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithId(1L, "student@example.com")));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.send(emailRequest());
            verify(emailSender, never()).send(any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(emailSender).send(any(Notification.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void send_asyncSenderWhoseTransactionRollsBack_isNeverDispatched() {
        when(emailSender.isAsynchronous()).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithId(1L, "student@example.com")));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.send(emailRequest());
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(emailSender, never()).send(any());
    }

    @Test
    void send_synchronousSenderInsideATransaction_isDispatchedImmediately() {
        when(emailSender.isAsynchronous()).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithId(1L, "student@example.com")));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.send(emailRequest());
            verify(emailSender).send(any(Notification.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void send_inApp_isPushedToTheUser_butOtherChannelsAreNot() {
        User user = userWithId(1L, "student@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.send(SendNotificationRequest.builder().userId(1L).subject("s").body("b")
                .channel(NotificationChannel.IN_APP).build());
        service.send(SendNotificationRequest.builder().userId(1L).subject("s").body("b")
                .channel(NotificationChannel.EMAIL).build());

        verify(realtimePublisher, times(1)).publishAfterCommit(eq(1L), eq(RealtimeEvent.NOTIFICATION_CREATED), any());
    }

    // --- sendAfterCommit: best-effort, after the caller's commit, never throws ---

    private SendNotificationRequest inAppRequest() {
        return SendNotificationRequest.builder().userId(1L).subject("Lesson booked").body("b")
                .channel(NotificationChannel.IN_APP).build();
    }

    @Test
    void sendAfterCommit_insideACallersTransaction_waitsForTheCommit_thenSendsInItsOwnTransaction() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithId(1L, "student@example.com")));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.sendAfterCommit(inAppRequest());
            verify(notificationRepository, never()).save(any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(notificationRepository, org.mockito.Mockito.atLeastOnce()).save(any(Notification.class));
        org.mockito.ArgumentCaptor<org.springframework.transaction.TransactionDefinition> definition =
                org.mockito.ArgumentCaptor.forClass(org.springframework.transaction.TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void sendAfterCommit_whenTheCallersTransactionRollsBack_sendsNothing() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.sendAfterCommit(inAppRequest());
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(userRepository, never()).findById(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendAfterCommit_aFailingSend_isLoggedNeverThrown() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatCode(() -> service.sendAfterCommit(inAppRequest()))
                .doesNotThrowAnyException();
    }
}
