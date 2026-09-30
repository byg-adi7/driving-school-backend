package com.drivingschool.backend.messaging.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ForbiddenException;
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
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagingServiceImplTest {

    private static final Long STUDENT_USER_ID = 1L;
    private static final Long INSTRUCTOR_USER_ID = 2L;

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private NotificationService notificationService;
    @Mock private RealtimePublisher realtimePublisher;

    private MessagingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MessagingServiceImpl(conversationRepository, messageRepository, studentProfileRepository,
                instructorProfileRepository, currentUserService, notificationService, realtimePublisher);
    }

    private School school(Long id) {
        School school = School.builder().active(true).build();
        ReflectionTestUtils.setField(school, "id", id);
        return school;
    }

    private User user(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private StudentProfile student(Long profileId, User user, School school) {
        StudentProfile student = StudentProfile.builder().firstName("Sam").lastName("Student").user(user).school(school).build();
        ReflectionTestUtils.setField(student, "id", profileId);
        return student;
    }

    private InstructorProfile instructor(Long profileId, User user, School school, boolean active) {
        InstructorProfile instructor = InstructorProfile.builder().firstName("Ina").lastName("Instructor")
                .user(user).school(school).active(active).build();
        ReflectionTestUtils.setField(instructor, "id", profileId);
        return instructor;
    }

    private Conversation conversation(StudentProfile student, InstructorProfile instructor) {
        Conversation conversation = Conversation.builder().school(student.getSchool()).student(student).instructor(instructor).build();
        ReflectionTestUtils.setField(conversation, "id", 70L);
        return conversation;
    }

    private void callerIsStudent() {
        when(currentUserService.hasRole(RoleName.STUDENT)).thenReturn(true);
        when(currentUserService.requireUserId()).thenReturn(STUDENT_USER_ID);
    }

    private void callerIsInstructor() {
        when(currentUserService.hasRole(RoleName.STUDENT)).thenReturn(false);
        when(currentUserService.requireUserId()).thenReturn(INSTRUCTOR_USER_ID);
    }

    // --- contacts ---

    @Test
    void listContacts_asStudent_returnsOnlyActiveInstructorsOfTheirSchool() {
        callerIsStudent();
        School school = school(5L);
        when(studentProfileRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(student(10L, user(STUDENT_USER_ID), school)));
        when(instructorProfileRepository.findBySchoolIdExcludingDeletedUsers(5L)).thenReturn(List.of(
                instructor(20L, user(2L), school, true), instructor(21L, user(3L), school, false)));

        List<ContactResponse> contacts = service.listContacts();

        assertThat(contacts).extracting(ContactResponse::getProfileId).containsExactly(20L);
    }

    @Test
    void listContacts_asInstructor_returnsTheirSchoolsStudents() {
        callerIsInstructor();
        School school = school(5L);
        when(instructorProfileRepository.findByUserId(INSTRUCTOR_USER_ID))
                .thenReturn(Optional.of(instructor(20L, user(INSTRUCTOR_USER_ID), school, true)));
        when(studentProfileRepository.findBySchoolIdExcludingDeletedUsers(5L)).thenReturn(List.of(student(10L, user(1L), school)));

        assertThat(service.listContacts()).extracting(ContactResponse::getProfileId).containsExactly(10L);
    }

    // --- start ---

    @Test
    void startConversation_asStudentWithInstructorOfSameSchool_createsIt() {
        callerIsStudent();
        School school = school(5L);
        StudentProfile me = student(10L, user(STUDENT_USER_ID), school);
        InstructorProfile them = instructor(20L, user(INSTRUCTOR_USER_ID), school, true);
        when(studentProfileRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(me));
        when(instructorProfileRepository.findById(20L)).thenReturn(Optional.of(them));
        when(conversationRepository.findByStudent_IdAndInstructor_Id(10L, 20L)).thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 70L);
            return c;
        });

        ConversationResponse response = service.startConversation(StartConversationRequest.builder().participantProfileId(20L).build());

        assertThat(response.getId()).isEqualTo(70L);
        assertThat(response.getCounterpartName()).isEqualTo("Ina Instructor");
        assertThat(response.getCounterpartRole()).isEqualTo("INSTRUCTOR");
    }

    @Test
    void startConversation_existingPair_isReusedNotDuplicated() {
        callerIsStudent();
        School school = school(5L);
        StudentProfile me = student(10L, user(STUDENT_USER_ID), school);
        InstructorProfile them = instructor(20L, user(INSTRUCTOR_USER_ID), school, true);
        when(studentProfileRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(me));
        when(instructorProfileRepository.findById(20L)).thenReturn(Optional.of(them));
        when(conversationRepository.findByStudent_IdAndInstructor_Id(10L, 20L)).thenReturn(Optional.of(conversation(me, them)));

        assertThat(service.startConversation(StartConversationRequest.builder().participantProfileId(20L).build()).getId())
                .isEqualTo(70L);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void startConversation_withInstructorOfAnotherSchool_isRejected() {
        callerIsStudent();
        when(studentProfileRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(student(10L, user(STUDENT_USER_ID), school(5L))));
        when(instructorProfileRepository.findById(20L)).thenReturn(Optional.of(instructor(20L, user(INSTRUCTOR_USER_ID), school(6L), true)));

        assertThatThrownBy(() -> service.startConversation(StartConversationRequest.builder().participantProfileId(20L).build()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("your own school");
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void startConversation_withInactiveInstructor_isRejected() {
        callerIsStudent();
        School school = school(5L);
        when(studentProfileRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(student(10L, user(STUDENT_USER_ID), school)));
        when(instructorProfileRepository.findById(20L)).thenReturn(Optional.of(instructor(20L, user(INSTRUCTOR_USER_ID), school, false)));

        assertThatThrownBy(() -> service.startConversation(StartConversationRequest.builder().participantProfileId(20L).build()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void startConversation_asInstructorWithDeletedStudent_isRejected() {
        callerIsInstructor();
        School school = school(5L);
        User deleted = user(STUDENT_USER_ID);
        deleted.softDelete();
        when(instructorProfileRepository.findByUserId(INSTRUCTOR_USER_ID))
                .thenReturn(Optional.of(instructor(20L, user(INSTRUCTOR_USER_ID), school, true)));
        when(studentProfileRepository.findById(10L)).thenReturn(Optional.of(student(10L, deleted, school)));
        when(conversationRepository.findByStudent_IdAndInstructor_Id(10L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startConversation(StartConversationRequest.builder().participantProfileId(10L).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no longer active");
    }

    // --- send / read ---

    @Test
    void sendMessage_asStudent_savesItUpdatesThePreviewAndNotifiesTheInstructor() {
        School school = school(5L);
        StudentProfile me = student(10L, user(STUDENT_USER_ID), school);
        InstructorProfile them = instructor(20L, user(INSTRUCTOR_USER_ID), school, true);
        Conversation conversation = conversation(me, them);
        when(currentUserService.requireUserId()).thenReturn(STUDENT_USER_ID);
        when(conversationRepository.findWithParticipantsById(70L)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageResponse response = service.sendMessage(70L, SendMessageRequest.builder().body("Can we move Friday's lesson?").build());

        assertThat(response.isMine()).isTrue();
        assertThat(conversation.getLastMessagePreview()).isEqualTo("Can we move Friday's lesson?");
        assertThat(conversation.getLastMessageAt()).isNotNull();
        ArgumentCaptor<SendNotificationRequest> notification = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService).sendAfterCommit(notification.capture());
        assertThat(notification.getValue().getUserId()).isEqualTo(INSTRUCTOR_USER_ID);
        assertThat(notification.getValue().getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(notification.getValue().getSubject()).isEqualTo("New message from Sam Student");
    }

    @Test
    void sendMessage_byNonParticipant_isRejected() {
        School school = school(5L);
        Conversation conversation = conversation(student(10L, user(STUDENT_USER_ID), school),
                instructor(20L, user(INSTRUCTOR_USER_ID), school, true));
        when(currentUserService.requireUserId()).thenReturn(99L);
        when(conversationRepository.findWithParticipantsById(70L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.sendMessage(70L, SendMessageRequest.builder().body("hi").build()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not part of this conversation");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_toADeletedAccount_isRejected() {
        School school = school(5L);
        User deletedInstructor = user(INSTRUCTOR_USER_ID);
        deletedInstructor.softDelete();
        Conversation conversation = conversation(student(10L, user(STUDENT_USER_ID), school),
                instructor(20L, deletedInstructor, school, true));
        when(currentUserService.requireUserId()).thenReturn(STUDENT_USER_ID);
        when(conversationRepository.findWithParticipantsById(70L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.sendMessage(70L, SendMessageRequest.builder().body("hi").build()))
                .isInstanceOf(BadRequestException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_longBody_isTruncatedInThePreviewAndNotification() {
        School school = school(5L);
        Conversation conversation = conversation(student(10L, user(STUDENT_USER_ID), school),
                instructor(20L, user(INSTRUCTOR_USER_ID), school, true));
        when(currentUserService.requireUserId()).thenReturn(INSTRUCTOR_USER_ID);
        when(conversationRepository.findWithParticipantsById(70L)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        String longBody = "x".repeat(500);

        service.sendMessage(70L, SendMessageRequest.builder().body(longBody).build());

        assertThat(conversation.getLastMessagePreview()).hasSize(Conversation.PREVIEW_LENGTH);
        ArgumentCaptor<SendNotificationRequest> notification = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService).sendAfterCommit(notification.capture());
        assertThat(notification.getValue().getUserId()).isEqualTo(STUDENT_USER_ID);
        assertThat(notification.getValue().getBody()).hasSize(103).endsWith("...");
    }

    @Test
    void getMessages_flagsTheCallersOwnMessages() {
        School school = school(5L);
        User studentUser = user(STUDENT_USER_ID);
        User instructorUser = user(INSTRUCTOR_USER_ID);
        Conversation conversation = conversation(student(10L, studentUser, school), instructor(20L, instructorUser, school, true));
        Message fromMe = Message.builder().conversation(conversation).sender(studentUser).body("mine").build();
        Message fromThem = Message.builder().conversation(conversation).sender(instructorUser).body("theirs").build();
        when(currentUserService.requireUserId()).thenReturn(STUDENT_USER_ID);
        when(conversationRepository.findWithParticipantsById(70L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationId(eq(70L), any())).thenReturn(new PageImpl<>(List.of(fromThem, fromMe)));

        List<MessageResponse> page = service.getMessages(70L, Pageable.unpaged()).getContent();

        assertThat(page).extracting(MessageResponse::isMine).containsExactly(false, true);
    }

    @Test
    void markRead_marksOnlyTheOtherParticipantsMessages() {
        School school = school(5L);
        Conversation conversation = conversation(student(10L, user(STUDENT_USER_ID), school),
                instructor(20L, user(INSTRUCTOR_USER_ID), school, true));
        when(currentUserService.requireUserId()).thenReturn(STUDENT_USER_ID);
        when(conversationRepository.findWithParticipantsById(70L)).thenReturn(Optional.of(conversation));

        service.markRead(70L);

        verify(messageRepository).markReadForRecipient(eq(70L), eq(STUDENT_USER_ID), any());
    }

    @Test
    void listMyConversations_includesUnreadCounts() {
        School school = school(5L);
        Conversation conversation = conversation(student(10L, user(STUDENT_USER_ID), school),
                instructor(20L, user(INSTRUCTOR_USER_ID), school, true));
        when(currentUserService.requireUserId()).thenReturn(STUDENT_USER_ID);
        when(conversationRepository.findAllForUser(STUDENT_USER_ID)).thenReturn(List.of(conversation));
        when(messageRepository.countUnreadByConversation(anyList(), eq(STUDENT_USER_ID)))
                .thenReturn(List.<Object[]>of(new Object[]{70L, 3L}));

        List<ConversationResponse> inbox = service.listMyConversations();

        assertThat(inbox).singleElement().satisfies(c -> {
            assertThat(c.getUnreadCount()).isEqualTo(3L);
            assertThat(c.getCounterpartName()).isEqualTo("Ina Instructor");
        });
    }

    // --- realtime ---

    @Test
    void sendMessage_pushesItToTheRecipientAndBackToTheSender_eachWithTheirOwnMineFlag() {
        School school = school(5L);
        Conversation conversation = conversation(student(10L, user(STUDENT_USER_ID), school),
                instructor(20L, user(INSTRUCTOR_USER_ID), school, true));
        when(currentUserService.requireUserId()).thenReturn(STUDENT_USER_ID);
        when(conversationRepository.findWithParticipantsById(70L)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        service.sendMessage(70L, SendMessageRequest.builder().body("On my way").build());

        ArgumentCaptor<Object> toInstructor = ArgumentCaptor.forClass(Object.class);
        verify(realtimePublisher).publishAfterCommit(eq(INSTRUCTOR_USER_ID), eq(RealtimeEvent.MESSAGE_CREATED), toInstructor.capture());
        assertThat(((MessageResponse) toInstructor.getValue()).isMine()).isFalse();
        ArgumentCaptor<Object> toSender = ArgumentCaptor.forClass(Object.class);
        verify(realtimePublisher).publishAfterCommit(eq(STUDENT_USER_ID), eq(RealtimeEvent.MESSAGE_CREATED), toSender.capture());
        assertThat(((MessageResponse) toSender.getValue()).isMine()).isTrue();
    }

    @Test
    void markRead_sendsAReadReceiptToTheOtherParticipant_onlyWhenSomethingWasUnread() {
        School school = school(5L);
        Conversation conversation = conversation(student(10L, user(STUDENT_USER_ID), school),
                instructor(20L, user(INSTRUCTOR_USER_ID), school, true));
        when(currentUserService.requireUserId()).thenReturn(INSTRUCTOR_USER_ID);
        when(conversationRepository.findWithParticipantsById(70L)).thenReturn(Optional.of(conversation));
        when(messageRepository.markReadForRecipient(eq(70L), eq(INSTRUCTOR_USER_ID), any())).thenReturn(2, 0);

        service.markRead(70L);
        service.markRead(70L);

        verify(realtimePublisher, org.mockito.Mockito.times(1))
                .publishAfterCommit(eq(STUDENT_USER_ID), eq(RealtimeEvent.CONVERSATION_READ), any());
    }
}
