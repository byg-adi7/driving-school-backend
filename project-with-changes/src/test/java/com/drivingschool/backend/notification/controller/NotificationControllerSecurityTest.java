package com.drivingschool.backend.notification.controller;

import com.drivingschool.backend.notification.dto.NotificationResponse;
import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.service.NotificationService;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.security.ApiVersioningFilter;
import com.drivingschool.backend.security.RateLimitingFilter;
import com.drivingschool.backend.security.SecurityTestUtils;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = NotificationController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(NotificationControllerSecurityTest.MethodSecurityTestConfig.class)
class NotificationControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private NotificationService notificationService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private SendNotificationRequest validRequest() {
        return SendNotificationRequest.builder()
                .userId(1L).subject("Reminder").body("Your lesson is tomorrow.")
                .channel(NotificationChannel.EMAIL).build();
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void send_asStudent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/send").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void send_asInstructor_isOk() throws Exception {
        when(notificationService.send(any())).thenReturn(NotificationResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/notifications/send")
                        .with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.INSTRUCTOR))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getMyNotifications_asAuthenticatedStudent_isOk() throws Exception {
        Page<NotificationResponse> page = new PageImpl<>(java.util.List.of(NotificationResponse.builder().id(1L).build()),
                PageRequest.of(0, 20), 1);
        when(notificationService.getMyNotifications(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/notifications/me"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void markAsRead_asAuthenticatedStudent_isOk() throws Exception {
        when(notificationService.markAsRead(anyLong())).thenReturn(NotificationResponse.builder().id(1L).build());

        mockMvc.perform(patch("/api/v1/notifications/1/read").with(csrf()))
                .andExpect(status().isOk());
    }
}
