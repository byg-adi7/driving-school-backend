package com.drivingschool.backend.messaging.service;

import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.messaging.dto.AnnouncementResponse;
import com.drivingschool.backend.messaging.dto.CreateAnnouncementRequest;
import com.drivingschool.backend.messaging.entity.Announcement;
import com.drivingschool.backend.messaging.repository.AnnouncementRepository;
import com.drivingschool.backend.notification.entity.Notification;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.enums.NotificationStatus;
import com.drivingschool.backend.notification.repository.NotificationRepository;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.validator.CallerSchoolScope;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceImplTest {

    @Mock private AnnouncementRepository announcementRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private AnnouncementEmailDispatcher emailDispatcher;
    @Mock private CurrentUserService currentUserService;
    @Mock private CallerSchoolScope callerSchoolScope;

    private AnnouncementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AnnouncementServiceImpl(announcementRepository, instructorProfileRepository, studentProfileRepository,
                notificationRepository, emailDispatcher, currentUserService, callerSchoolScope);
    }

    private School school() {
        School school = School.builder().active(true).build();
        ReflectionTestUtils.setField(school, "id", 5L);
        return school;
    }

    private StudentProfile student(Long id, School school) {
        User user = User.builder().email("s" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        StudentProfile student = StudentProfile.builder().firstName("S").lastName("" + id).user(user).school(school).build();
        ReflectionTestUtils.setField(student, "id", id);
        return student;
    }

    private void stubCreate(School school, List<StudentProfile> students) {
        InstructorProfile instructor = InstructorProfile.builder().firstName("Ina").lastName("Instructor").school(school).active(true).build();
        ReflectionTestUtils.setField(instructor, "id", 20L);
        when(currentUserService.requireUserId()).thenReturn(2L);
        when(instructorProfileRepository.findByUserId(2L)).thenReturn(Optional.of(instructor));
        when(announcementRepository.save(any(Announcement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentProfileRepository.findBySchoolIdExcludingDeletedUsers(5L)).thenReturn(students);
        AtomicLong ids = new AtomicLong(100);
        when(notificationRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<Notification> saved = inv.getArgument(0);
            saved.forEach(n -> ReflectionTestUtils.setField(n, "id", ids.incrementAndGet()));
            return saved;
        });
    }

    private CreateAnnouncementRequest request() {
        return CreateAnnouncementRequest.builder().subject("Road closure").body("The test route is closed on Monday.").build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void create_storesAnSentInAppAndAPendingEmailNotificationPerStudent() {
        School school = school();
        stubCreate(school, List.of(student(1L, school), student(2L, school)));

        AnnouncementResponse response = service.create(request());

        assertThat(response.getRecipientCount()).isEqualTo(2);
        assertThat(response.getInstructorName()).isEqualTo("Ina Instructor");
        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository, times(2)).saveAll(saved.capture());
        List<Notification> inApp = saved.getAllValues().get(0);
        List<Notification> email = saved.getAllValues().get(1);
        assertThat(inApp).hasSize(2).allSatisfy(n -> {
            assertThat(n.getChannel()).isEqualTo(NotificationChannel.IN_APP);
            assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(n.getSubject()).isEqualTo("Announcement from Ina Instructor: Road closure");
        });
        assertThat(email).hasSize(2).allSatisfy(n -> {
            assertThat(n.getChannel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING);
        });
        assertThat(email).extracting(Notification::getRecipientAddress).containsExactly("s1@example.com", "s2@example.com");
    }

    @Test
    void create_insideATransaction_emailsOnlyAfterCommit() {
        School school = school();
        stubCreate(school, List.of(student(1L, school)));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.create(request());
            verify(emailDispatcher, never()).dispatch(anyList());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(emailDispatcher).dispatch(List.of(102L));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void create_withNoStudents_sendsNothing() {
        stubCreate(school(), List.of());

        assertThat(service.create(request()).getRecipientCount()).isZero();
        verify(emailDispatcher, never()).dispatch(anyList());
    }

    @Test
    void listForMySchool_scopedCaller_getsOnlyTheirSchool() {
        when(callerSchoolScope.callerSchoolId()).thenReturn(Optional.of(5L));
        when(announcementRepository.findBySchoolId(eq(5L), any())).thenReturn(new PageImpl<>(List.of()));

        service.listForMySchool(Pageable.unpaged());

        verify(announcementRepository, never()).findAllNewestFirst(any());
    }

    @Test
    void listForMySchool_bootstrapAdmin_getsEverySchool() {
        when(callerSchoolScope.callerSchoolId()).thenReturn(Optional.empty());
        when(announcementRepository.findAllNewestFirst(any())).thenReturn(new PageImpl<>(List.of()));

        service.listForMySchool(Pageable.unpaged());

        verify(announcementRepository, never()).findBySchoolId(any(), any());
    }
}
