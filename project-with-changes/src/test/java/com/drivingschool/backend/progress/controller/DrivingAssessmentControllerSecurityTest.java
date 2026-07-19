package com.drivingschool.backend.progress.controller;

import com.drivingschool.backend.progress.dto.CreateDrivingAssessmentRequest;
import com.drivingschool.backend.progress.dto.DrivingAssessmentResponse;
import com.drivingschool.backend.progress.dto.UpdateDrivingAssessmentFeedbackRequest;
import com.drivingschool.backend.progress.enums.AssessmentResult;
import com.drivingschool.backend.progress.service.DrivingAssessmentService;
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

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = DrivingAssessmentController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(DrivingAssessmentControllerSecurityTest.MethodSecurityTestConfig.class)
class DrivingAssessmentControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private DrivingAssessmentService drivingAssessmentService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }

    private CreateDrivingAssessmentRequest createRequest() {
        CreateDrivingAssessmentRequest request = new CreateDrivingAssessmentRequest();
        request.setStudentId(1L);
        request.setAssessmentDate(LocalDateTime.now());
        request.setScore(85);
        request.setResult(AssessmentResult.PASSED);
        return request;
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void createAssessment_asStudent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/driving-assessments").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createAssessment_asAdmin_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/driving-assessments").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAssessment_asInstructor_isCreated() throws Exception {
        when(drivingAssessmentService.createAssessment(any(), anyLong()))
                .thenReturn(DrivingAssessmentResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/driving-assessments")
                        .with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.INSTRUCTOR))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void updateFeedback_asStudent_isForbidden() throws Exception {
        UpdateDrivingAssessmentFeedbackRequest request = new UpdateDrivingAssessmentFeedbackRequest();
        request.setFeedback("edited");

        mockMvc.perform(patch("/api/v1/driving-assessments/1/feedback").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getInstructorAssessments_asStudent_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/driving-assessments/instructor/1"))
                .andExpect(status().isForbidden());
    }
}
