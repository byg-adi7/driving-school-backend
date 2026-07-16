package com.drivingschool.backend.instructor.controller;

import com.drivingschool.backend.instructor.dto.InstructorProfileResponse;
import com.drivingschool.backend.instructor.dto.UpdateInstructorActiveStatusRequest;
import com.drivingschool.backend.instructor.dto.UpdateInstructorProfileRequest;
import com.drivingschool.backend.instructor.service.InstructorProfileService;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = InstructorProfileController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(InstructorProfileControllerSecurityTest.MethodSecurityTestConfig.class)
class InstructorProfileControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private InstructorProfileService instructorProfileService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private UpdateInstructorProfileRequest updateRequest() {
        return UpdateInstructorProfileRequest.builder()
                .firstName("Jane").lastName("Doe").phone("123")
                .specialization("Highway").yearsExperience(5).bio("Bio").build();
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getMyProfile_asStudent_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/instructors/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void getMyProfile_asInstructor_isOk() throws Exception {
        when(instructorProfileService.getMyProfile()).thenReturn(InstructorProfileResponse.builder().id(1L).build());

        mockMvc.perform(get("/api/v1/instructors/me"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void updateMyProfile_asStudent_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/instructors/me").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void updateMyProfile_asInstructor_isOk() throws Exception {
        when(instructorProfileService.updateMyProfile(any()))
                .thenReturn(InstructorProfileResponse.builder().id(1L).build());

        mockMvc.perform(put("/api/v1/instructors/me").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void getBySchool_asInstructor_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/instructors/school/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getBySchool_asAdmin_isOk() throws Exception {
        when(instructorProfileService.getBySchool(1L)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/instructors/school/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void updateActiveStatus_asInstructor_isForbidden() throws Exception {
        UpdateInstructorActiveStatusRequest request = UpdateInstructorActiveStatusRequest.builder().active(false).build();

        mockMvc.perform(patch("/api/v1/instructors/1/active").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateActiveStatus_asAdmin_isOk() throws Exception {
        when(instructorProfileService.updateActiveStatus(anyLong(), any()))
                .thenReturn(InstructorProfileResponse.builder().id(1L).build());
        UpdateInstructorActiveStatusRequest request = UpdateInstructorActiveStatusRequest.builder().active(false).build();

        mockMvc.perform(patch("/api/v1/instructors/1/active").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
