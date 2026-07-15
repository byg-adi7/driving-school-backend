package com.drivingschool.backend.quiz.controller;

import com.drivingschool.backend.quiz.dto.CreateQuizQuestionRequest;
import com.drivingschool.backend.quiz.dto.CreateQuizRequest;
import com.drivingschool.backend.quiz.dto.QuizResponse;
import com.drivingschool.backend.quiz.dto.QuizSubmissionResponse;
import com.drivingschool.backend.quiz.dto.SubmitQuizRequest;
import com.drivingschool.backend.quiz.enums.QuestionType;
import com.drivingschool.backend.quiz.service.QuizService;
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

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves each {@code @PreAuthorize} on {@link QuizController} is actually enforced at
 * the HTTP layer. Unlike BookingController, these are plain role checks with no
 * per-resource ownership bean to mock.
 */
@WebMvcTest(
        controllers = QuizController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(QuizControllerSecurityTest.MethodSecurityTestConfig.class)
class QuizControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private QuizService quizService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private CreateQuizRequest validCreateQuizRequest() {
        return CreateQuizRequest.builder()
                .courseId(1L).title("Road Rules").passingScore(70).maxAttempts(3).build();
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void create_asStudent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/quizzes").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validCreateQuizRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_asInstructor_isOk() throws Exception {
        when(quizService.create(any(), anyLong(), anyString())).thenReturn(QuizResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/quizzes")
                        .with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.INSTRUCTOR))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validCreateQuizRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void addQuestion_asStudent_isForbidden() throws Exception {
        CreateQuizQuestionRequest request = CreateQuizQuestionRequest.builder()
                .questionText("What does a red light mean?")
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .correctAnswer("Stop")
                .points(1)
                .questionOrder(1)
                .build();

        mockMvc.perform(post("/api/v1/quizzes/1/questions").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void publish_asStudent_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/quizzes/1/publish").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void submit_asInstructor_isForbidden() throws Exception {
        SubmitQuizRequest request = SubmitQuizRequest.builder()
                .studentId(1L).answers(Map.of(1L, "Stop")).build();

        mockMvc.perform(post("/api/v1/quizzes/1/submit").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void submit_asStudent_isOk() throws Exception {
        when(quizService.submit(any(), any(), anyLong(), anyString()))
                .thenReturn(QuizSubmissionResponse.builder().id(1L).build());
        SubmitQuizRequest request = SubmitQuizRequest.builder()
                .studentId(1L).answers(Map.of(1L, "Stop")).build();

        mockMvc.perform(post("/api/v1/quizzes/1/submit")
                        .with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.STUDENT))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
