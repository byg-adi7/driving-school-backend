package com.drivingschool.backend.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RealtimePublisherTest {

    @Mock private SimpMessagingTemplate messagingTemplate;

    private RealtimePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new RealtimePublisher(messagingTemplate);
    }

    @Test
    void outsideATransaction_sendsToTheUsersEventQueueImmediately() {
        publisher.publishAfterCommit(42L, RealtimeEvent.NOTIFICATION_CREATED, "payload");

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(eq("42"), eq("/queue/events"), event.capture());
        assertThat(event.getValue()).isEqualTo(new RealtimeEvent(RealtimeEvent.NOTIFICATION_CREATED, "payload"));
    }

    @Test
    void insideATransaction_waitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publishAfterCommit(42L, RealtimeEvent.MESSAGE_CREATED, "payload");
            verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(messagingTemplate).convertAndSendToUser(eq("42"), eq("/queue/events"), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void aRolledBackTransaction_sendsNothing() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publishAfterCommit(42L, RealtimeEvent.MESSAGE_CREATED, "payload");
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void aFailedPush_neverBreaksTheCaller() {
        doThrow(new MessagingException("broker down")).when(messagingTemplate)
                .convertAndSendToUser(anyString(), anyString(), any());

        assertThatCode(() -> publisher.publishAfterCommit(42L, RealtimeEvent.MESSAGE_CREATED, "payload"))
                .doesNotThrowAnyException();
    }
}
