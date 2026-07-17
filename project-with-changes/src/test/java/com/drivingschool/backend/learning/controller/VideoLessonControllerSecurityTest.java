package com.drivingschool.backend.learning.controller;

import com.drivingschool.backend.learning.dto.CreateVideoLessonRequest;
import com.drivingschool.backend.learning.dto.UpdateVideoLessonRequest;
import com.drivingschool.backend.learning.dto.VideoLessonResponse;
import com.drivingschool.backend.learning.service.VideoLessonService;
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
        controllers = VideoLessonController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(VideoLessonControllerSecurityTest.MethodSecurityTestConfig.class)
class VideoLessonControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private VideoLessonService videoLessonService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private CreateVideoLessonRequest validCreateRequest() {
        return CreateVideoLessonRequest.builder()
                .courseId(1L).title("Lesson 1").videoUrl("https://example.com/v1").lessonOrder(1).build();
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void create_asStudent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/video-lessons").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_asInstructor_isCreated() throws Exception {
        when(videoLessonService.create(any(), anyLong(), anyString()))
                .thenReturn(VideoLessonResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/video-lessons")
                        .with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.INSTRUCTOR))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void update_asStudent_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/video-lessons/1").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdateVideoLessonRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void publish_asStudent_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/video-lessons/1/publish").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_asStudent_isOk() throws Exception {
        when(videoLessonService.getById(anyLong(), anyLong(), anyString()))
                .thenReturn(VideoLessonResponse.builder().id(1L).build());

        mockMvc.perform(get("/api/v1/video-lessons/1")
                        .with(SecurityTestUtils.withUser(1L, RoleName.STUDENT)))
                .andExpect(status().isOk());
    }

    @Test
    void getByCourse_asStudent_isOk() throws Exception {
        mockMvc.perform(get("/api/v1/video-lessons/course/1")
                        .with(SecurityTestUtils.withUser(1L, RoleName.STUDENT)))
                .andExpect(status().isOk());
    }
}
