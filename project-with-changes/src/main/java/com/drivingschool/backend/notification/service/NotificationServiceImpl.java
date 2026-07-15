package com.drivingschool.backend.notification.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.notification.dto.NotificationResponse;
import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.entity.Notification;
import com.drivingschool.backend.notification.enums.NotificationStatus;
import com.drivingschool.backend.notification.mapper.NotificationMapper;
import com.drivingschool.backend.notification.repository.NotificationRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final List<NotificationSender> notificationSenders;
    private final NotificationMapper notificationMapper;

    public NotificationServiceImpl(UserRepository userRepository,
                                   NotificationRepository notificationRepository,
                                   List<NotificationSender> notificationSenders,
                                   NotificationMapper notificationMapper) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.notificationSenders = notificationSenders;
        this.notificationMapper = notificationMapper;
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
                        : user.getEmail())
                .build();

        Notification saved = notificationRepository.save(notification);

        notificationSenders.stream()
                .filter(sender -> sender.supports(request.getChannel()))
                .findFirst()
                .ifPresentOrElse(
                        sender -> sender.send(saved),
                        () -> {
                            saved.markFailed("No sender configured for channel: " + request.getChannel());
                            notificationRepository.save(saved);
                        }
                );

        return notificationMapper.toResponse(
                notificationRepository.findById(saved.getId()).orElse(saved));
    }
}
