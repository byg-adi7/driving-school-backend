package com.drivingschool.backend.school.controller;

import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.dto.SchoolDeletionRequestResponse;
import com.drivingschool.backend.school.service.SchoolDeletionRequestService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SchoolDeletionRequestController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(SchoolDeletionRequestControllerSecurityTest.MethodSecurityTestConfig.class)
class SchoolDeletionRequestControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private SchoolDeletionRequestService schoolDeletionRequestService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void listPending_asStudent_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/school-deletion-requests"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void listPending_asInstructor_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/school-deletion-requests"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listPending_asAdmin_isOk() throws Exception {
        Page<SchoolDeletionRequestResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(schoolDeletionRequestService.listPending(any(), anyLong())).thenReturn(page);

        mockMvc.perform(get("/api/v1/school-deletion-requests")
                        .with(SecurityTestUtils.withUser(1L, RoleName.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void approve_asStudent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/school-deletion-requests/1/approve").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void approve_asAdmin_isOk() throws Exception {
        when(schoolDeletionRequestService.approve(anyLong(), anyLong(), any()))
                .thenReturn(SchoolDeletionRequestResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/school-deletion-requests/1/approve").with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void reject_asStudent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/school-deletion-requests/1/reject").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void reject_asAdmin_isOk() throws Exception {
        when(schoolDeletionRequestService.reject(anyLong(), anyLong(), any()))
                .thenReturn(SchoolDeletionRequestResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/school-deletion-requests/1/reject").with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.ADMIN)))
                .andExpect(status().isOk());
    }
}
