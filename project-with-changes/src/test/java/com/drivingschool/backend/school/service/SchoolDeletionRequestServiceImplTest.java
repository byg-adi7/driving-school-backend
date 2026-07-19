package com.drivingschool.backend.school.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.service.NotificationService;
import com.drivingschool.backend.school.dto.ReviewSchoolDeletionRequest;
import com.drivingschool.backend.school.dto.SchoolDeletionRequestResponse;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.entity.SchoolDeletionRequest;
import com.drivingschool.backend.school.enums.SchoolDeletionRequestStatus;
import com.drivingschool.backend.school.mapper.SchoolDeletionRequestMapper;
import com.drivingschool.backend.school.repository.SchoolDeletionRequestRepository;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolDeletionRequestServiceImplTest {

    @Mock private SchoolDeletionRequestRepository requestRepository;
    @Mock private UserRepository userRepository;
    @Mock private SchoolRepository schoolRepository;
    @Mock private SchoolAdminCascadeDeletionService cascadeDeletionService;
    @Mock private NotificationService notificationService;
    private final SchoolDeletionRequestMapper mapper = new SchoolDeletionRequestMapper();

    private SchoolDeletionRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SchoolDeletionRequestServiceImpl(requestRepository, userRepository, schoolRepository,
                cascadeDeletionService, mapper, notificationService);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private School ownedSchool(Long id, User owner) {
        School school = School.builder().name("My School").address("1 Main St").active(true).owningAdmin(owner).build();
        ReflectionTestUtils.setField(school, "id", id);
        return school;
    }

    // --- requestOwnSchoolDeletion ---

    @Test
    void requestOwnSchoolDeletion_nonBootstrapAdminWithSchool_createsPendingRequestAndNotifiesBothChannels() {
        User caller = userWithId(1L);
        School owned = ownedSchool(10L, caller);
        User bootstrap = userWithId(99L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(caller));
        when(schoolRepository.findByOwningAdminId(1L)).thenReturn(Optional.of(owned));
        when(requestRepository.existsBySchoolIdAndStatus(10L, SchoolDeletionRequestStatus.PENDING)).thenReturn(false);
        when(requestRepository.save(any(SchoolDeletionRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByBootstrapAdminTrue()).thenReturn(Optional.of(bootstrap));

        service.requestOwnSchoolDeletion(1L);

        ArgumentCaptor<SendNotificationRequest> captor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService, times(2)).send(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(SendNotificationRequest::getChannel)
                .containsExactlyInAnyOrder(NotificationChannel.EMAIL, NotificationChannel.IN_APP);
    }

    @Test
    void requestOwnSchoolDeletion_bootstrapAdmin_throwsBadRequestException() {
        User bootstrap = userWithId(1L);
        ReflectionTestUtils.setField(bootstrap, "bootstrapAdmin", true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(bootstrap));

        assertThatThrownBy(() -> service.requestOwnSchoolDeletion(1L)).isInstanceOf(BadRequestException.class);

        verify(requestRepository, never()).save(any());
    }

    @Test
    void requestOwnSchoolDeletion_adminWithoutSchool_throwsBadRequestException() {
        User caller = userWithId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> service.requestOwnSchoolDeletion(1L)).isInstanceOf(BadRequestException.class);

        verify(requestRepository, never()).save(any());
    }

    @Test
    void requestOwnSchoolDeletion_whenAlreadyPending_throwsBadRequestException() {
        User caller = userWithId(1L);
        School owned = ownedSchool(10L, caller);

        when(userRepository.findById(1L)).thenReturn(Optional.of(caller));
        when(schoolRepository.findByOwningAdminId(1L)).thenReturn(Optional.of(owned));
        when(requestRepository.existsBySchoolIdAndStatus(10L, SchoolDeletionRequestStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.requestOwnSchoolDeletion(1L)).isInstanceOf(BadRequestException.class);

        verify(requestRepository, never()).save(any());
    }

    // --- approve / reject ---

    @Test
    void approve_asBootstrap_marksApprovedAndInvokesCascade() {
        User bootstrap = userWithId(1L);
        ReflectionTestUtils.setField(bootstrap, "bootstrapAdmin", true);
        User admin = userWithId(2L);
        School school = ownedSchool(10L, admin);
        SchoolDeletionRequest request = SchoolDeletionRequest.builder()
                .school(school).schoolName("My School").status(SchoolDeletionRequestStatus.PENDING)
                .requestedBy(admin).requestedByEmail(admin.getEmail()).build();
        ReflectionTestUtils.setField(request, "id", 5L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(bootstrap));
        when(requestRepository.findById(5L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(SchoolDeletionRequest.class))).thenReturn(request);

        SchoolDeletionRequestResponse response = service.approve(5L, 1L, null);

        assertThat(response.getStatus()).isEqualTo(SchoolDeletionRequestStatus.APPROVED);
        verify(cascadeDeletionService).execute(10L);
    }

    @Test
    void approve_asNonBootstrap_throwsBadRequestException() {
        User caller = userWithId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> service.approve(5L, 1L, null)).isInstanceOf(BadRequestException.class);

        verify(cascadeDeletionService, never()).execute(any());
    }

    @Test
    void approve_whenAlreadyReviewed_throwsBadRequestException() {
        User bootstrap = userWithId(1L);
        ReflectionTestUtils.setField(bootstrap, "bootstrapAdmin", true);
        SchoolDeletionRequest request = SchoolDeletionRequest.builder()
                .schoolName("My School").status(SchoolDeletionRequestStatus.APPROVED)
                .requestedByEmail("a@example.com").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(bootstrap));
        when(requestRepository.findById(5L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approve(5L, 1L, null)).isInstanceOf(BadRequestException.class);

        verify(cascadeDeletionService, never()).execute(any());
    }

    @Test
    void reject_asBootstrap_marksRejectedWithoutCascade() {
        User bootstrap = userWithId(1L);
        ReflectionTestUtils.setField(bootstrap, "bootstrapAdmin", true);
        SchoolDeletionRequest request = SchoolDeletionRequest.builder()
                .schoolName("My School").status(SchoolDeletionRequestStatus.PENDING)
                .requestedByEmail("a@example.com").build();
        ReflectionTestUtils.setField(request, "id", 5L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(bootstrap));
        when(requestRepository.findById(5L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any(SchoolDeletionRequest.class))).thenReturn(request);

        ReviewSchoolDeletionRequest body = ReviewSchoolDeletionRequest.builder().reviewNotes("Not approved").build();
        SchoolDeletionRequestResponse response = service.reject(5L, 1L, body);

        assertThat(response.getStatus()).isEqualTo(SchoolDeletionRequestStatus.REJECTED);
        verify(cascadeDeletionService, never()).execute(any());
    }
}
