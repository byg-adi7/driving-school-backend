package com.drivingschool.backend.school.controller;

import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.dto.CreateSchoolWithAdminRequest;
import com.drivingschool.backend.school.dto.SchoolDeletionRequestResponse;
import com.drivingschool.backend.school.dto.SchoolResponse;
import com.drivingschool.backend.school.dto.SchoolWithAdminResponse;
import com.drivingschool.backend.school.service.SchoolDeletionRequestService;
import com.drivingschool.backend.school.service.SchoolService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SchoolController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(SchoolControllerSecurityTest.MethodSecurityTestConfig.class)
class SchoolControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private SchoolService schoolService;
    @MockBean private SchoolDeletionRequestService schoolDeletionRequestService;
    @MockBean private com.drivingschool.backend.school.service.SchoolLogoService schoolLogoService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private CreateSchoolWithAdminRequest createRequest() {
        return CreateSchoolWithAdminRequest.builder()
                .schoolName("Downtown Driving School").schoolAddress("123 Main St")
                .adminEmail("owner@dds.example").adminPassword("SecurePass123!").build();
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void create_asStudent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/schools").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_isOk() throws Exception {
        when(schoolService.createWithAdmin(any())).thenReturn(SchoolWithAdminResponse.builder()
                .school(SchoolResponse.builder().id(1L).build()).adminUserId(50L).adminEmail("owner@dds.example").build());

        mockMvc.perform(post("/api/v1/schools").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    void getById_asStudent_isOk() throws Exception {
        when(schoolService.getById(eq(1L), anyLong(), any())).thenReturn(SchoolResponse.builder().id(1L).build());

        mockMvc.perform(get("/api/v1/schools/1").with(SecurityTestUtils.withUser(1L, RoleName.STUDENT)))
                .andExpect(status().isOk());
    }

    @Test
    void getById_asAdmin_isOk() throws Exception {
        when(schoolService.getById(eq(1L), anyLong(), any())).thenReturn(SchoolResponse.builder().id(1L).build());

        mockMvc.perform(get("/api/v1/schools/1").with(SecurityTestUtils.withUser(1L, RoleName.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void getAllActive_asAdmin_isOk() throws Exception {
        when(schoolService.getAllActive(anyLong(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/schools").with(SecurityTestUtils.withUser(1L, RoleName.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void delete_asStudent_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/schools/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asAdmin_isOk() throws Exception {
        mockMvc.perform(delete("/api/v1/schools/1").with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void requestOwnDeletion_asStudent_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/schools/me").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestOwnDeletion_asAdmin_isAccepted() throws Exception {
        when(schoolDeletionRequestService.requestOwnSchoolDeletion(anyLong()))
                .thenReturn(SchoolDeletionRequestResponse.builder().id(1L).build());

        mockMvc.perform(delete("/api/v1/schools/me").with(csrf())
                        .with(SecurityTestUtils.withUser(50L, RoleName.ADMIN)))
                .andExpect(status().isAccepted());
    }
}
