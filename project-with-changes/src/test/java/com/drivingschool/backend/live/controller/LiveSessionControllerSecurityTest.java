package com.drivingschool.backend.live.controller;

import com.drivingschool.backend.live.dto.AttendanceResponse;
import com.drivingschool.backend.live.dto.CreateLiveSessionRequest;
import com.drivingschool.backend.live.dto.LiveSessionResponse;
import com.drivingschool.backend.live.dto.RegisterAttendanceRequest;
import com.drivingschool.backend.live.service.LiveSessionService;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.security.ApiVersioningFilter;
import com.drivingschool.backend.security.RateLimitingFilter;
import com.drivingschool.backend.security.SecurityTestUtils;
import com.drivingschool.backend.security.jwt.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = LiveSessionController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(LiveSessionControllerSecurityTest.MethodSecurityTestConfig.class)
class LiveSessionControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private LiveSessionService liveSessionService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper;
        }
    }

    private CreateLiveSessionRequest validScheduleRequest() {
        return CreateLiveSessionRequest.builder()
                .instructorId(1L).schoolId(1L).title("Intro session")
                .scheduledAt(LocalDateTime.now().plusDays(1)).durationMinutes(60).build();
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void schedule_asStudent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/live-sessions").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validScheduleRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void schedule_asInstructor_isOk() throws Exception {
        when(liveSessionService.schedule(any(), anyLong(), anyString()))
                .thenReturn(LiveSessionResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/live-sessions")
                        .with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.INSTRUCTOR))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validScheduleRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void updateStatus_asStudent_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/live-sessions/1/status").with(csrf()).param("status", "IN_PROGRESS"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void register_asInstructor_isForbidden() throws Exception {
        RegisterAttendanceRequest request = RegisterAttendanceRequest.builder().studentId(1L).build();

        mockMvc.perform(post("/api/v1/live-sessions/1/register").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_asStudent_isOk() throws Exception {
        when(liveSessionService.register(any(), any(), anyLong(), anyString()))
                .thenReturn(AttendanceResponse.builder().id(1L).build());
        RegisterAttendanceRequest request = RegisterAttendanceRequest.builder().studentId(1L).build();

        mockMvc.perform(post("/api/v1/live-sessions/1/register")
                        .with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.STUDENT))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void markPresent_asStudent_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/live-sessions/1/attendance/1/present").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getAttendance_asStudent_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/live-sessions/1/attendance"))
                .andExpect(status().isForbidden());
    }
}
