package com.drivingschool.backend.progress.controller;

import com.drivingschool.backend.progress.dto.AdvanceStageRequest;
import com.drivingschool.backend.progress.dto.LicenseWorkflowResponse;
import com.drivingschool.backend.progress.enums.LicenseStage;
import com.drivingschool.backend.progress.service.LicenseWorkflowService;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = LicenseWorkflowController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(LicenseWorkflowControllerSecurityTest.MethodSecurityTestConfig.class)
class LicenseWorkflowControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private LicenseWorkflowService licenseWorkflowService;

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
    void initialize_asStudent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/progress/license/students/1/initialize").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void initialize_asAdmin_isOk() throws Exception {
        when(licenseWorkflowService.initializeForStudent(anyLong()))
                .thenReturn(LicenseWorkflowResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/progress/license/students/1/initialize")
                        .with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.ADMIN)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void updateTheoryProgress_asInstructor_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/progress/license/students/1/theory-progress")
                        .with(csrf()).param("progressPercent", "50"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void markQuizPassed_asStudent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/progress/license/students/1/quiz-passed").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void advanceStage_asStudent_isForbidden() throws Exception {
        AdvanceStageRequest request = AdvanceStageRequest.builder().targetStage(LicenseStage.THEORY_COMPLETED).build();

        mockMvc.perform(put("/api/v1/progress/license/students/1/advance").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
