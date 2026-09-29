package com.drivingschool.backend.messaging.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.messaging.dto.ContactResponse;
import com.drivingschool.backend.messaging.dto.ConversationResponse;
import com.drivingschool.backend.messaging.dto.MessageResponse;
import com.drivingschool.backend.messaging.dto.SendMessageRequest;
import com.drivingschool.backend.messaging.dto.StartConversationRequest;
import com.drivingschool.backend.messaging.entity.Conversation;
import com.drivingschool.backend.messaging.entity.Message;
import com.drivingschool.backend.messaging.repository.ConversationRepository;
import com.drivingschool.backend.messaging.repository.MessageRepository;
import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.service.NotificationService;
import com.drivingschool.backend.realtime.RealtimeEvent;
import com.drivingschool.backend.realtime.RealtimePublisher;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Private messaging between a student and an instructor of the same school.
 *
 * Replaces the only channel students had before - lesson questions - for general
 * communication: a student couldn't even list their school's instructors to address
 * a question to, so questions went out unassigned, reached no instructor's inbox, and
 * only surfaced in the admin-facing by-status listing.
 */
@Slf4j
@Service
public class MessagingServiceImpl implements MessagingService {

    private static final int NOTIFICATION_PREVIEW_LENGTH = 100;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;
    private final RealtimePublisher realtimePublisher;

    public MessagingServiceImpl(ConversationRepository conversationRepository,
                                MessageRepository messageRepository,
                                StudentProfileRepository studentProfileRepository,
                                InstructorProfileRepository instructorProfileRepository,
                                CurrentUserService currentUserService,
                                NotificationService notificationService,
                                RealtimePublisher realtimePublisher) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.currentUserService = currentUserService;
        this.notificationService = notificationService;
        this.realtimePublisher = realtimePublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactResponse> listContacts() {
        if (currentUserService.hasRole(RoleName.STUDENT)) {
            StudentProfile me = requireStudent();
            return instructorProfileRepository.findBySchoolIdExcludingDeletedUsers(me.getSchool().getId()).stream()
                    .filter(InstructorProfile::isActive)
                    .map(i -> ContactResponse.builder()
                            .profileId(i.getId()).firstName(i.getFirstName()).lastName(i.getLastName()).build())
                    .toList();
        }
        InstructorProfile me = requireInstructor();
        return studentProfileRepository.findBySchoolIdExcludingDeletedUsers(me.getSchool().getId()).stream()
                .map(s -> ContactResponse.builder()
                        .profileId(s.getId()).firstName(s.getFirstName()).lastName(s.getLastName()).build())
                .toList();
    }

    @Override
    @Transactional
    public ConversationResponse startConversation(StartConversationRequest request) {
        StudentProfile student;
        InstructorProfile instructor;
        Long targetId = request.getParticipantProfileId();

        if (currentUserService.hasRole(RoleName.STUDENT)) {
            student = requireStudent();
            instructor = instructorProfileRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("InstructorProfile", "id", targetId));
            if (!instructor.isActive()) {
                throw new BadRequestException("This instructor is not currently active");
            }
        } else {
            instructor = requireInstructor();
            student = studentProfileRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("StudentProfile", "id", targetId));
        }

        if (!student.getSchool().getId().equals(instructor.getSchool().getId())) {
            throw new BadRequestException("You can only message people at your own school");
        }

        Conversation conversation = conversationRepository
                .findByStudent_IdAndInstructor_Id(student.getId(), instructor.getId())
                .orElseGet(() -> {
                    requireActiveAccount(student.getUser());
                    requireActiveAccount(instructor.getUser());
                    return conversationRepository.save(Conversation.builder()
                            .school(student.getSchool())
                            .student(student)
                            .instructor(instructor)
                            .build());
                });

        Long myUserId = currentUserService.requireUserId();
        return toResponse(conversation, myUserId, unreadCounts(List.of(conversation), myUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> listMyConversations() {
        Long myUserId = currentUserService.requireUserId();
        List<Conversation> conversations = conversationRepository.findAllForUser(myUserId);
        Map<Long, Long> unread = unreadCounts(conversations, myUserId);
        return conversations.stream()
                .map(c -> toResponse(c, myUserId, unread))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(Long conversationId, Pageable pageable) {
        Long myUserId = currentUserService.requireUserId();
        requireParticipant(conversationId, myUserId);
        return messageRepository.findByConversationId(conversationId, pageable)
                .map(m -> toResponse(m, myUserId));
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(Long conversationId, SendMessageRequest request) {
        Long myUserId = currentUserService.requireUserId();
        Conversation conversation = requireParticipant(conversationId, myUserId);

        boolean senderIsStudent = conversation.getStudent().getUser().getId().equals(myUserId);
        User sender = senderIsStudent ? conversation.getStudent().getUser() : conversation.getInstructor().getUser();
        User recipient = senderIsStudent ? conversation.getInstructor().getUser() : conversation.getStudent().getUser();
        requireActiveAccount(recipient);

        Message saved = messageRepository.save(Message.builder()
                .conversation(conversation)
                .sender(sender)
                .body(request.getBody())
                .build());
        conversation.recordMessage(LocalDateTime.now(), request.getBody());

        String senderName = senderIsStudent
                ? fullName(conversation.getStudent().getFirstName(), conversation.getStudent().getLastName())
                : fullName(conversation.getInstructor().getFirstName(), conversation.getInstructor().getLastName());
        notifyRecipient(recipient, senderName, request.getBody(), conversationId);
        // Both sides: the recipient sees it arrive, and the sender's other tabs/devices stay in sync.
        realtimePublisher.publishAfterCommit(recipient.getId(), RealtimeEvent.MESSAGE_CREATED, toResponse(saved, recipient.getId()));
        realtimePublisher.publishAfterCommit(myUserId, RealtimeEvent.MESSAGE_CREATED, toResponse(saved, myUserId));

        return toResponse(saved, myUserId);
    }

    @Override
    @Transactional
    public void markRead(Long conversationId) {
        Long myUserId = currentUserService.requireUserId();
        Conversation conversation = requireParticipant(conversationId, myUserId);
        LocalDateTime readAt = LocalDateTime.now();
        if (messageRepository.markReadForRecipient(conversationId, myUserId, readAt) > 0) {
            // Read receipt for the other participant - only when something was actually unread.
            Long otherUserId = conversation.getStudent().getUser().getId().equals(myUserId)
                    ? conversation.getInstructor().getUser().getId()
                    : conversation.getStudent().getUser().getId();
            realtimePublisher.publishAfterCommit(otherUserId, RealtimeEvent.CONVERSATION_READ,
                    Map.of("conversationId", conversationId, "readByUserId", myUserId, "readAt", readAt));
        }
    }

    // ------------------------------------------------------------------

    private StudentProfile requireStudent() {
        Long userId = currentUserService.requireUserId();
        return studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found for user ID: " + userId));
    }

    private InstructorProfile requireInstructor() {
        Long userId = currentUserService.requireUserId();
        return instructorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor profile not found for user ID: " + userId));
    }

    private Conversation requireParticipant(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findWithParticipantsById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));
        if (!conversation.hasParticipant(userId)) {
            throw new BadRequestException("You are not part of this conversation");
        }
        return conversation;
    }

    private void requireActiveAccount(User user) {
        if (user.isDeleted() || !user.isEnabled()) {
            throw new BadRequestException("This person's account is no longer active");
        }
    }

    private Map<Long, Long> unreadCounts(List<Conversation> conversations, Long myUserId) {
        if (conversations.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = conversations.stream().map(Conversation::getId).toList();
        return messageRepository.countUnreadByConversation(ids, myUserId).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private ConversationResponse toResponse(Conversation c, Long myUserId, Map<Long, Long> unread) {
        boolean iAmStudent = c.getStudent().getUser().getId().equals(myUserId);
        return ConversationResponse.builder()
                .id(c.getId())
                .studentProfileId(c.getStudent().getId())
                .instructorProfileId(c.getInstructor().getId())
                .counterpartName(iAmStudent
                        ? fullName(c.getInstructor().getFirstName(), c.getInstructor().getLastName())
                        : fullName(c.getStudent().getFirstName(), c.getStudent().getLastName()))
                .counterpartRole(iAmStudent ? RoleName.INSTRUCTOR.name() : RoleName.STUDENT.name())
                .lastMessagePreview(c.getLastMessagePreview())
                .lastMessageAt(c.getLastMessageAt())
                .unreadCount(unread.getOrDefault(c.getId(), 0L))
                .build();
    }

    private MessageResponse toResponse(Message m, Long myUserId) {
        Long senderId = m.getSender().getId();
        return MessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversation().getId())
                .senderUserId(senderId)
                .mine(senderId.equals(myUserId))
                .body(m.getBody())
                .sentAt(m.getCreatedAt())
                .readAt(m.getReadAt())
                .build();
    }

    private String fullName(String first, String last) {
        return first + " " + last;
    }

    private void notifyRecipient(User recipient, String senderName, String body, Long conversationId) {
        String preview = body.length() <= NOTIFICATION_PREVIEW_LENGTH
                ? body : body.substring(0, NOTIFICATION_PREVIEW_LENGTH) + "...";
        try {
            notificationService.send(SendNotificationRequest.builder()
                    .userId(recipient.getId())
                    .subject("New message from " + senderName)
                    .body(preview)
                    .channel(NotificationChannel.IN_APP)
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to send new-message notification: conversationId={}, recipientId={}",
                    conversationId, recipient.getId(), ex);
        }
    }
}
