package com.drivingschool.backend.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Pushes an event to one user's open WebSocket sessions (all of their tabs/devices).
 *
 * Always after the caller's transaction commits: a client reacting to an event (e.g.
 * refetching the thread) must never race data that isn't committed yet, and a
 * rolled-back change must never be announced. Best-effort by design - a user with no
 * open session simply doesn't get the push, and still sees everything through the
 * REST API (notifications, inbox) on their next fetch.
 *
 * Uses Spring's in-memory broker, so a push only reaches sessions connected to THIS
 * instance. Correct for a single Render instance; running several would need a shared
 * relay (e.g. Redis pub/sub) - see DEPLOYMENT.md.
 */
@Slf4j
@Component
public class RealtimePublisher {

    static final String EVENTS_QUEUE = "/queue/events";

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishAfterCommit(Long userId, String type, Object payload) {
        RealtimeEvent event = new RealtimeEvent(type, payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(userId, event);
                }
            });
            return;
        }
        send(userId, event);
    }

    private void send(Long userId, RealtimeEvent event) {
        try {
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), EVENTS_QUEUE, event);
        } catch (Exception ex) {
            log.warn("Failed to push realtime {} event to user {}", event.type(), userId, ex);
        }
    }
}
