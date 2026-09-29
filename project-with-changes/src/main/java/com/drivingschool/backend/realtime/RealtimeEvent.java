package com.drivingschool.backend.realtime;

/**
 * Everything pushed to a client arrives on its single private queue
 * (/user/queue/events) in this envelope; {@code type} says what {@code payload} is.
 */
public record RealtimeEvent(String type, Object payload) {

    /** payload: MessageResponse (with "mine" computed for the receiving user). */
    public static final String MESSAGE_CREATED = "MESSAGE_CREATED";

    /** payload: {conversationId, readByUserId, readAt} - the other participant read my messages. */
    public static final String CONVERSATION_READ = "CONVERSATION_READ";

    /** payload: NotificationResponse - a new IN_APP notification for me. */
    public static final String NOTIFICATION_CREATED = "NOTIFICATION_CREATED";

    /** payload: AnnouncementResponse - a new announcement in my school. */
    public static final String ANNOUNCEMENT_CREATED = "ANNOUNCEMENT_CREATED";
}
