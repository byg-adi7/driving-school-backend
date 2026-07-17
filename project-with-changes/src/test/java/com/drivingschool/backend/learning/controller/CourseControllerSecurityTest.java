package com.drivingschool.backend.learning.controller;

import com.drivingschool.backend.learning.dto.CourseResponse;
import com.drivingschool.backend.learning.dto.CreateCourseRequest;
import com.drivingschool.backend.learning.dto.UpdateCourseRequest;
import com.drivingschool.backend.learning.service.CourseService;
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
        controllers = CourseController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(CourseControllerSecurityTest.MethodSecurityTestConfig.class)
class CourseControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CourseService courseService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private CreateCourseRequest validCreateRequest() {
        return CreateCourseRequest.builder().title("Road Rules").build();
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void create_asStudent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/courses").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_asInstructor_isCreated() throws Exception {
        when(courseService.create(any(), anyLong(), anyString())).thenReturn(CourseResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/courses")
                        .with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.INSTRUCTOR))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void update_asStudent_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/courses/1").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdateCourseRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void publish_asStudent_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/courses/1/publish").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void archive_asStudent_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/courses/1/archive").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_asStudent_isOk() throws Exception {
        when(courseService.getById(anyLong(), anyLong(), anyString())).thenReturn(CourseResponse.builder().id(1L).build());

        mockMvc.perform(get("/api/v1/courses/1")
                        .with(SecurityTestUtils.withUser(1L, RoleName.STUDENT)))
                .andExpect(status().isOk());
    }

    @Test
    void getPublished_asStudent_isOk() throws Exception {
        mockMvc.perform(get("/api/v1/courses")
                        .with(SecurityTestUtils.withUser(1L, RoleName.STUDENT)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getMine_asStudent_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/courses/mine"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getMine_asAdmin_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/courses/mine"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMine_asInstructor_isOk() throws Exception {
        mockMvc.perform(get("/api/v1/courses/mine")
                        .with(SecurityTestUtils.withUser(1L, RoleName.INSTRUCTOR)))
                .andExpect(status().isOk());
    }
}
