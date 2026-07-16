package com.drivingschool.backend.student.controller;

import com.drivingschool.backend.security.ApiVersioningFilter;
import com.drivingschool.backend.security.RateLimitingFilter;
import com.drivingschool.backend.security.jwt.JwtAuthenticationFilter;
import com.drivingschool.backend.student.dto.StudentProfileResponse;
import com.drivingschool.backend.student.dto.UpdateStudentProfileRequest;
import com.drivingschool.backend.student.dto.UpdateStudentStatusRequest;
import com.drivingschool.backend.student.enums.StudentStatus;
import com.drivingschool.backend.student.service.StudentProfileService;
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

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = StudentProfileController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(StudentProfileControllerSecurityTest.MethodSecurityTestConfig.class)
class StudentProfileControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private StudentProfileService studentProfileService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            return mapper;
        }
    }

    private UpdateStudentProfileRequest updateRequest() {
        return UpdateStudentProfileRequest.builder()
                .firstName("Jane").lastName("Doe").phone("123")
                .dateOfBirth(LocalDate.of(2000, 1, 1)).profileImageUrl("http://img").build();
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void getMyProfile_asInstructor_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/students/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getMyProfile_asStudent_isOk() throws Exception {
        when(studentProfileService.getMyProfile()).thenReturn(StudentProfileResponse.builder().id(1L).build());

        mockMvc.perform(get("/api/v1/students/me"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void updateMyProfile_asInstructor_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/students/me").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void updateMyProfile_asStudent_isOk() throws Exception {
        when(studentProfileService.updateMyProfile(any()))
                .thenReturn(StudentProfileResponse.builder().id(1L).build());

        mockMvc.perform(put("/api/v1/students/me").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updateRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getBySchool_asStudent_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/students/school/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getBySchool_asAdmin_isOk() throws Exception {
        when(studentProfileService.getBySchool(1L)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/students/school/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void updateStatus_asStudent_isForbidden() throws Exception {
        UpdateStudentStatusRequest request = UpdateStudentStatusRequest.builder().status(StudentStatus.SUSPENDED).build();

        mockMvc.perform(patch("/api/v1/students/1/status").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStatus_asAdmin_isOk() throws Exception {
        when(studentProfileService.updateStatus(anyLong(), any()))
                .thenReturn(StudentProfileResponse.builder().id(1L).build());
        UpdateStudentStatusRequest request = UpdateStudentStatusRequest.builder().status(StudentStatus.SUSPENDED).build();

        mockMvc.perform(patch("/api/v1/students/1/status").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
