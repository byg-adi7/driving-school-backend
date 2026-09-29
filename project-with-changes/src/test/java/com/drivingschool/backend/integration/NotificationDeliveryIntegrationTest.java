package com.drivingschool.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Best-effort notifications (NotificationService.sendAfterCommit) are delivered only
 * after the triggering transaction commits - so they can only be observed with a real
 * server and real commits, not in AbstractIntegrationTest's rolled-back transactions.
 */
class NotificationDeliveryIntegrationTest extends AbstractRealServerIntegrationTest {

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> notifications(String token) {
        return (List<Map<String, Object>>) getData("/notifications/me", token).get("content");
    }

    @Test
    void welcomeBookingAndMessageNotificationsArriveAfterCommit() {
        String suffix = String.valueOf(System.nanoTime());
        String bootstrapToken = loginAsBootstrapAdmin();
        Object schoolId = createSchool(bootstrapToken, suffix);
        register(bootstrapToken, schoolId, "INSTRUCTOR", "nd.instructor." + suffix + "@example.com");
        register(bootstrapToken, schoolId, "STUDENT", "nd.student." + suffix + "@example.com");
        String instructorToken = login("nd.instructor." + suffix + "@example.com", PASSWORD);
        String studentToken = login("nd.student." + suffix + "@example.com", PASSWORD);
        Object instructorProfileId = getData("/auth/me", instructorToken).get("instructorProfileId");
        Object studentProfileId = getData("/auth/me", studentToken).get("studentProfileId");

        // Registration committed, then the welcome notification was delivered - it can see
        // the new user row because it runs after that commit, in its own transaction.
        assertThat(notifications(studentToken)).extracting(n -> n.get("subject")).contains("Welcome to Aidly!");

        // A booking notifies the student once the booking has committed.
        post("/bookings", instructorToken, Map.of(
                "studentId", studentProfileId,
                "instructorId", instructorProfileId,
                "scheduledAt", LocalDateTime.now().plusDays(2).withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "durationMinutes", 60,
                "bookingType", "ROAD_LESSON"));
        Map<String, Object> bookingNotification = notifications(studentToken).stream()
                .filter(n -> "Upcoming practical lesson scheduled".equals(n.get("subject")))
                .findFirst().orElseThrow();
        assertThat(bookingNotification.get("channel")).isEqualTo("IN_APP");
        assertThat(bookingNotification.get("readAt")).isNull();

        Map<String, Object> markedRead = exchange(HttpMethod.PATCH,
                "/notifications/" + bookingNotification.get("id") + "/read", studentToken, null);
        assertThat(markedRead.get("readAt")).isNotNull();

        // A message notifies its recipient once the message has committed.
        Map<String, Object> conversation = post("/conversations", studentToken, Map.of("participantProfileId", instructorProfileId));
        post("/conversations/" + conversation.get("id") + "/messages", studentToken, Map.of("body", "See you Friday"));
        assertThat(notifications(instructorToken)).extracting(n -> (String) n.get("subject"))
                .anyMatch(subject -> subject.startsWith("New message from"));
    }
}
