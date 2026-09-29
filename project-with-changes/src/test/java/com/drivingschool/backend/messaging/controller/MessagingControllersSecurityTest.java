package com.drivingschool.backend.messaging.controller;

import com.drivingschool.backend.messaging.dto.AnnouncementResponse;
import com.drivingschool.backend.messaging.dto.ConversationResponse;
import com.drivingschool.backend.messaging.dto.MessageResponse;
import com.drivingschool.backend.messaging.service.AnnouncementService;
import com.drivingschool.backend.messaging.service.MessagingService;
import com.drivingschool.backend.security.ApiVersioningFilter;
import com.drivingschool.backend.security.RateLimitingFilter;
import com.drivingschool.backend.security.jwt.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {ConversationController.class, AnnouncementController.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(MessagingControllersSecurityTest.MethodSecurityTestConfig.class)
class MessagingControllersSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private MessagingService messagingService;
    @MockBean private AnnouncementService announcementService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    // --- conversations: students and instructors only ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void conversations_asAdmin_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/conversations")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void conversations_asStudent_isOk() throws Exception {
        when(messagingService.listMyConversations()).thenReturn(List.of(ConversationResponse.builder().id(1L).build()));
        mockMvc.perform(get("/api/v1/conversations")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void contacts_asInstructor_isOk() throws Exception {
        when(messagingService.listContacts()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/conversations/contacts")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void sendMessage_asStudent_isCreated() throws Exception {
        when(messagingService.sendMessage(any(), any())).thenReturn(MessageResponse.builder().id(9L).build());
        mockMvc.perform(post("/api/v1/conversations/1/messages").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("body", "Hello"))))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void sendMessage_blankBody_isBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/1/messages").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("body", " "))))
                .andExpect(status().isBadRequest());
    }

    // --- announcements: instructors post, the school reads ---

    @Test
    @WithMockUser(roles = "STUDENT")
    void createAnnouncement_asStudent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/announcements").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("subject", "Hi", "body", "All students"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void createAnnouncement_asInstructor_isCreated() throws Exception {
        when(announcementService.create(any())).thenReturn(AnnouncementResponse.builder().id(3L).recipientCount(12).build());
        mockMvc.perform(post("/api/v1/announcements").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("subject", "Road closure", "body", "Test route closed Monday."))))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void listAnnouncements_asStudent_isOk() throws Exception {
        when(announcementService.listForMySchool(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        mockMvc.perform(get("/api/v1/announcements")).andExpect(status().isOk());
    }
}
