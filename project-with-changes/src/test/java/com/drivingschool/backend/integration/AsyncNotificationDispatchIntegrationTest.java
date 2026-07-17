package com.drivingschool.backend.integration;

import com.drivingschool.backend.notification.dto.NotificationResponse;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.enums.NotificationStatus;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.dto.SchoolResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves EmailNotificationSender.send() actually runs off the request thread.
 * No mail server is configured in tests, so a synchronous send would fail the
 * real SMTP attempt inline and the response would come back with status
 * FAILED. Instead the response must come back PENDING - the dispatch (and its
 * eventual SENT/FAILED update) happens on the background executor after the
 * response is already on its way. (Verifying the *eventual* FAILED update
 * would require the write to be visible to a second DB connection before
 * AbstractIntegrationTest's per-test transaction commits, which it never does
 * by design - the PENDING assertion alone is sufficient and doesn't fight
 * that rollback-based test isolation.)
 */
class AsyncNotificationDispatchIntegrationTest extends AbstractIntegrationTest {

    @Test
    void emailNotification_respondsBeforeDispatchCompletes() throws Exception {
        String adminToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        SchoolResponse school = createSchool(adminToken, "Async Notification Driving School " + System.nanoTime());
        Person student = registerAndIdentify(adminToken, school.getId(), RoleName.STUDENT,
                "async.notify." + System.nanoTime() + "@example.com", null);

        MvcResult result = mockMvc.perform(post("/api/v1/notifications/send")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", student.userId(),
                                "subject", "Test email dispatch",
                                "body", "This should be dispatched asynchronously",
                                "channel", NotificationChannel.EMAIL.name()))))
                .andExpect(status().isCreated())
                .andReturn();

        NotificationResponse response = parse(result, NotificationResponse.class);
        assertThat(response.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }
}
