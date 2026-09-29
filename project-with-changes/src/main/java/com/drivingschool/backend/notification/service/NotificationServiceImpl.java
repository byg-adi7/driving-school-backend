package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.notification.dto.NotificationResponse;
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
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final List<NotificationSender> notificationSenders;
    private final NotificationMapper notificationMapper;
    private final CurrentUserService currentUserService;
    private final CallerSchoolScope callerSchoolScope;
    private final RealtimePublisher realtimePublisher;
    private final TransactionTemplate requiresNewTransaction;

    public NotificationServiceImpl(UserRepository userRepository,
                                   NotificationRepository notificationRepository,
                                   List<NotificationSender> notificationSenders,
                                   NotificationMapper notificationMapper,
                                   CurrentUserService currentUserService,
                                   CallerSchoolScope callerSchoolScope,
                                   RealtimePublisher realtimePublisher,
                                   PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.notificationSenders = notificationSenders;
        this.notificationMapper = notificationMapper;
        this.currentUserService = currentUserService;
        this.callerSchoolScope = callerSchoolScope;
        this.realtimePublisher = realtimePublisher;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Callers used to call send() inside their own transaction and catch any exception.
    // That catch never helped: send() is @Transactional and JOINS the caller's
    // transaction, so an exception leaving it marks the whole transaction rollback-only
    // on the way out - the caller's commit then throws UnexpectedRollbackException, and
    // the booking/message/account fails anyway. (A REQUIRES_NEW send() inside the
    // caller's transaction isn't the answer either: it couldn't see the caller's
    // uncommitted rows - e.g. the brand-new user a welcome notification is for.)
    // So: wait for the caller's commit, then send in a fresh transaction.
    @Override
    public void sendAfterCommit(SendNotificationRequest request) {
        Runnable deliver = () -> {
            try {
                requiresNewTransaction.executeWithoutResult(status -> send(request));
            } catch (Exception ex) {
                log.warn("Failed to deliver {} notification to user {} ({})",
                        request.getChannel(), request.getUserId(), request.getSubject(), ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver.run();
                }
            });
            return;
        }
        deliver.run();
    }

    @Override
    @Transactional
    public NotificationResponse send(SendNotificationRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getUserId()));

        Notification notification = Notification.builder()
                .user(user)
                .subject(request.getSubject())
                .body(request.getBody())
                .channel(request.getChannel())
                .status(NotificationStatus.PENDING)
                .recipientAddress(request.getRecipientAddress() != null
                        ? request.getRecipientAddress()
                        : defaultRecipientAddress(user, request.getChannel()))
                .build();

        Notification saved = notificationRepository.save(notification);

        notificationSenders.stream()
                .filter(sender -> sender.supports(request.getChannel()))
                .findFirst()
                .ifPresentOrElse(
                        sender -> dispatch(sender, saved),
                        () -> {
                            saved.markFailed("No sender configured for channel: " + request.getChannel());
                            notificationRepository.save(saved);
                        }
                );

        NotificationResponse response = notificationMapper.toResponse(
                notificationRepository.findById(saved.getId()).orElse(saved));
        if (request.getChannel() == NotificationChannel.IN_APP) {
            realtimePublisher.publishAfterCommit(user.getId(), RealtimeEvent.NOTIFICATION_CREATED, response);
        }
        return response;
    }

    // An @Async sender dispatched mid-transaction raced the commit: its save() on the
    // background thread tried to UPDATE a row that wasn't committed yet and failed with
    // ObjectOptimisticLockingFailureException, leaving the delivery record stuck at
    // PENDING (seen live on booking-created SMS). Deferring to afterCommit fixes that,
    // and also means a rolled-back caller (e.g. a booking that failed to save) never
    // sends a message about something that doesn't exist.
    private void dispatch(NotificationSender sender, Notification notification) {
        if (sender.isAsynchronous() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sender.send(notification);
                }
            });
            return;
        }
        sender.send(notification);
    }

    @Override
    @Transactional
    public NotificationResponse sendAsCaller(SendNotificationRequest request) {
        callerSchoolScope.requireSameSchoolAsUser(request.getUserId());
        return send(request);
    }

    // SMS has no use for a user's email, and email/in-app have no use for a
    // phone number - each channel resolves its own notion of "address" from
    // the user rather than sharing one fallback. Student/instructor phone
    // numbers are optional (added at registration or later via profile
    // settings), so this can still come back null - SmsNotificationSender
    // treats a blank recipientAddress as "no phone number on file" and fails
    // gracefully rather than sending anywhere.
    private String defaultRecipientAddress(User user, NotificationChannel channel) {
        if (channel != NotificationChannel.SMS) {
            return user.getEmail();
        }
        if (user.getStudentProfile() != null) {
            return user.getStudentProfile().getPhone();
        }
        if (user.getInstructorProfile() != null) {
            return user.getInstructorProfile().getPhone();
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(Pageable pageable) {
        Long userId = currentUserService.requireUserId();
        // EMAIL/SMS/PUSH rows are delivery records for other channels, not app-UI
        // content - only IN_APP notifications belong in this list.
        return notificationRepository.findByUserIdAndChannelOrderByCreatedAtDesc(userId, NotificationChannel.IN_APP, pageable)
                .map(notificationMapper::toResponse);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        Long userId = currentUserService.requireUserId();
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        if (!notification.getUser().getId().equals(userId)) {
            throw new BadRequestException("You do not have access to this notification");
        }

        notification.markRead();
        return notificationMapper.toResponse(notificationRepository.save(notification));
    }
}
