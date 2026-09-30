package com.drivingschool.backend.integration;

import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.dto.SchoolResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Student-instructor messaging and school announcements through the real HTTP layer
 * against Postgres + Redis. Everyone is registered through the bootstrap admin to stay
 * inside the 10-per-minute auth-endpoint rate limit (register + login count against it).
 */
class MessagingIntegrationTest extends AbstractIntegrationTest {

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @SuppressWarnings("unchecked")
    private <T> T data(MvcResult result) throws Exception {
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (T) body.get("data");
    }

    @Test
    void studentAndInstructorConverse_andInstructorAnnouncesToTheWholeSchool() throws Exception {
        String bootstrapToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        SchoolResponse schoolA = createSchool(bootstrapToken, "Messaging School A");
        SchoolResponse schoolB = createSchool(bootstrapToken, "Messaging School B");
        Person instructor = registerAndIdentify(bootstrapToken, schoolA.getId(), RoleName.INSTRUCTOR, "msg.instructor@example.com", "LIC-MSG-1");
        Person student = registerAndIdentify(bootstrapToken, schoolA.getId(), RoleName.STUDENT, "msg.student@example.com", null);
        Person classmate = registerAndIdentify(bootstrapToken, schoolA.getId(), RoleName.STUDENT, "msg.classmate@example.com", null);
        Person otherSchoolInstructor = registerAndIdentify(bootstrapToken, schoolB.getId(), RoleName.INSTRUCTOR, "msg.other@example.com", "LIC-MSG-2");

        // A student can now see who their instructors are - previously only admins could.
        List<Map<String, Object>> contacts = data(mockMvc.perform(get("/api/v1/conversations/contacts")
                        .header("Authorization", bearer(student.token())))
                .andExpect(status().isOk()).andReturn());
        assertThat(contacts).extracting(c -> ((Number) c.get("profileId")).longValue())
                .containsExactly(instructor.profileId());

        // Opening a conversation is idempotent per pair...
        Map<String, Object> conversation = data(mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", bearer(student.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("participantProfileId", instructor.profileId()))))
                .andExpect(status().isOk()).andReturn());
        Object conversationId = conversation.get("id");
        Map<String, Object> again = data(mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", bearer(student.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("participantProfileId", instructor.profileId()))))
                .andExpect(status().isOk()).andReturn());
        assertThat(again.get("id")).isEqualTo(conversationId);

        // ...and never crosses schools.
        mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", bearer(student.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("participantProfileId", otherSchoolInstructor.profileId()))))
                .andExpect(status().isForbidden());

        // The student writes; it reaches THIS instructor - unread, with a preview.
        mockMvc.perform(post("/api/v1/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(student.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("body", "Can we move Friday's lesson to 3pm?"))))
                .andExpect(status().isCreated());

        List<Map<String, Object>> instructorInbox = data(mockMvc.perform(get("/api/v1/conversations")
                        .header("Authorization", bearer(instructor.token())))
                .andExpect(status().isOk()).andReturn());
        assertThat(instructorInbox).singleElement().satisfies(c -> {
            assertThat(((Number) c.get("unreadCount")).longValue()).isEqualTo(1L);
            assertThat(c.get("lastMessagePreview")).isEqualTo("Can we move Friday's lesson to 3pm?");
            assertThat(c.get("counterpartRole")).isEqualTo("STUDENT");
        });
        // (The "New message from" notification is sent after commit, so it never appears in
        // this rolled-back harness - NotificationDeliveryIntegrationTest covers it.)

        // Another student of the same school can't read it.
        mockMvc.perform(get("/api/v1/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(classmate.token())))
                .andExpect(status().isForbidden());

        // The instructor reads and replies.
        mockMvc.perform(post("/api/v1/conversations/" + conversationId + "/read")
                        .header("Authorization", bearer(instructor.token())))
                .andExpect(status().isOk());
        List<Map<String, Object>> afterRead = data(mockMvc.perform(get("/api/v1/conversations")
                        .header("Authorization", bearer(instructor.token())))
                .andExpect(status().isOk()).andReturn());
        assertThat(((Number) afterRead.get(0).get("unreadCount")).longValue()).isZero();

        mockMvc.perform(post("/api/v1/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(instructor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("body", "3pm works - see you then."))))
                .andExpect(status().isCreated());

        Map<String, Object> thread = data(mockMvc.perform(get("/api/v1/conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(student.token())))
                .andExpect(status().isOk()).andReturn());
        List<Map<String, Object>> messages = (List<Map<String, Object>>) thread.get("content");
        assertThat(messages).extracting(m -> m.get("body"))
                .containsExactly("3pm works - see you then.", "Can we move Friday's lesson to 3pm?");
        assertThat(messages).extracting(m -> m.get("mine")).containsExactly(false, true);

        // Announcements: instructors only, reaching every student of the school.
        mockMvc.perform(post("/api/v1/announcements")
                        .header("Authorization", bearer(student.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("subject", "Hi", "body", "Not allowed"))))
                .andExpect(status().isForbidden());

        Map<String, Object> announcement = data(mockMvc.perform(post("/api/v1/announcements")
                        .header("Authorization", bearer(instructor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("subject", "Test route closed", "body", "The Monday test route is closed for roadworks."))))
                .andExpect(status().isCreated()).andReturn());
        assertThat(((Number) announcement.get("recipientCount")).intValue()).isEqualTo(2);

        String classmateAnnouncements = mockMvc.perform(get("/api/v1/announcements")
                        .header("Authorization", bearer(classmate.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(classmateAnnouncements).contains("Test route closed");
        String classmateNotifications = mockMvc.perform(get("/api/v1/notifications/me")
                        .header("Authorization", bearer(classmate.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(classmateNotifications).contains("Test route closed");

        String otherSchoolAnnouncements = mockMvc.perform(get("/api/v1/announcements")
                        .header("Authorization", bearer(otherSchoolInstructor.token())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(otherSchoolAnnouncements).doesNotContain("Test route closed");
    }
}
