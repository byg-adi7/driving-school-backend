package com.drivingschool.backend.messaging.service;

import com.drivingschool.backend.email.ResendEmailClient;
import com.drivingschool.backend.notification.entity.Notification;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.enums.NotificationStatus;
import com.drivingschool.backend.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementEmailDispatcherTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private ResendEmailClient resendEmailClient;

    private AnnouncementEmailDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new AnnouncementEmailDispatcher(notificationRepository, resendEmailClient, "no-reply@aidly.test");
    }

    private List<Notification> pendingEmails(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(i -> {
            Notification n = Notification.builder()
                    .subject("Announcement").body("Body").channel(NotificationChannel.EMAIL)
                    .status(NotificationStatus.PENDING).recipientAddress("s" + i + "@example.com").build();
            ReflectionTestUtils.setField(n, "id", i);
            return n;
        }).toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatch_150Recipients_sendsTwoBatchesOfAtMost100AndMarksAllSent() {
        List<Notification> emails = pendingEmails(150);
        List<Long> ids = emails.stream().map(Notification::getId).toList();
        when(notificationRepository.findAllById(ids)).thenReturn(emails);

        dispatcher.dispatch(ids);

        ArgumentCaptor<List<ResendEmailClient.BatchEmail>> batches = ArgumentCaptor.forClass(List.class);
        verify(resendEmailClient, times(2)).sendBatch(eq("no-reply@aidly.test"), batches.capture());
        assertThat(batches.getAllValues()).extracting(List::size).containsExactly(100, 50);
        assertThat(batches.getAllValues().get(0).get(0).toAddress()).isEqualTo("s1@example.com");
        assertThat(emails).allSatisfy(n -> assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatch_aFailedBatch_marksOnlyThatBatchFailed() {
        List<Notification> emails = pendingEmails(150);
        List<Long> ids = emails.stream().map(Notification::getId).toList();
        when(notificationRepository.findAllById(ids)).thenReturn(emails);
        doThrow(new RuntimeException("429 Too Many Requests"))
                .doNothing()
                .when(resendEmailClient).sendBatch(eq("no-reply@aidly.test"), anyList());

        dispatcher.dispatch(ids);

        assertThat(emails.subList(0, 100)).allSatisfy(n -> {
            assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(n.getFailureReason()).contains("429");
        });
        assertThat(emails.subList(100, 150)).allSatisfy(n -> assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT));
        verify(notificationRepository, times(2)).saveAll(anyList());
    }
}
