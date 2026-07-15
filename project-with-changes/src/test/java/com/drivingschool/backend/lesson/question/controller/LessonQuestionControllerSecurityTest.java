package com.drivingschool.backend.lesson.question.controller;

import com.drivingschool.backend.lesson.question.dto.QuestionResponse;
import com.drivingschool.backend.lesson.question.dto.RespondToQuestionRequest;
import com.drivingschool.backend.lesson.question.dto.SubmitQuestionRequest;
import com.drivingschool.backend.lesson.question.dto.UpdateQuestionStatusRequest;
import com.drivingschool.backend.lesson.question.enums.QuestionStatus;
import com.drivingschool.backend.lesson.question.service.LessonQuestionStatusHistoryService;
import com.drivingschool.backend.lesson.question.service.LessonQuestionSubmissionService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = LessonQuestionController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(LessonQuestionControllerSecurityTest.MethodSecurityTestConfig.class)
class LessonQuestionControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private LessonQuestionSubmissionService questionService;
    @MockBean private LessonQuestionStatusHistoryService statusHistoryService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private SubmitQuestionRequest validSubmitRequest() {
        SubmitQuestionRequest request = new SubmitQuestionRequest();
        request.setSubject("Parking question");
        request.setQuestionBody("How do I parallel park on a hill safely?");
        return request;
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void submitQuestion_asInstructor_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/lesson-questions").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validSubmitRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitQuestion_asStudent_isOk() throws Exception {
        when(questionService.submitQuestion(any(), anyLong())).thenReturn(QuestionResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/lesson-questions")
                        .with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.STUDENT))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validSubmitRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void respondToQuestion_asStudent_isForbidden() throws Exception {
        RespondToQuestionRequest request = new RespondToQuestionRequest();
        request.setResponse("Slow down and use your mirrors before turning.");

        mockMvc.perform(post("/api/v1/lesson-questions/1/respond").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void updateQuestionStatus_asStudent_isForbidden() throws Exception {
        UpdateQuestionStatusRequest request = new UpdateQuestionStatusRequest();
        request.setNewStatus(QuestionStatus.CLOSED);

        mockMvc.perform(put("/api/v1/lesson-questions/1/status").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void getMyQuestions_asInstructor_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/lesson-questions/my-questions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getAssignedQuestions_asStudent_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/lesson-questions/assigned"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getQuestionsByStatus_asStudent_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/lesson-questions/status/PENDING"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getPendingQuestions_asStudent_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/lesson-questions/pending"))
                .andExpect(status().isForbidden());
    }
}
