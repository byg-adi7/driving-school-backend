package com.drivingschool.backend.gamification.controller;

import com.drivingschool.backend.gamification.dto.GamificationSummaryResponse;
import com.drivingschool.backend.gamification.service.GamificationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = GamificationController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(GamificationControllerSecurityTest.MethodSecurityTestConfig.class)
class GamificationControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private GamificationService gamificationService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void getMySummary_asInstructor_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/gamification/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMySummary_asStudent_isOk() throws Exception {
        when(gamificationService.getMySummary(anyLong()))
                .thenReturn(GamificationSummaryResponse.builder().studentId(1L).totalPoints(0).badges(List.of()).build());

        mockMvc.perform(get("/api/v1/gamification/me")
                        .with(SecurityTestUtils.withUser(1L, RoleName.STUDENT)))
                .andExpect(status().isOk());
    }

    @Test
    void getStudentSummary_asInstructor_isOk() throws Exception {
        when(gamificationService.getStudentSummary(anyLong(), anyLong(), any()))
                .thenReturn(GamificationSummaryResponse.builder().studentId(1L).totalPoints(0).badges(List.of()).build());

        mockMvc.perform(get("/api/v1/gamification/students/1")
                        .with(SecurityTestUtils.withUser(5L, RoleName.INSTRUCTOR)))
                .andExpect(status().isOk());
    }

    @Test
    void getSchoolLeaderboard_asStudent_isOk() throws Exception {
        Page<?> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(gamificationService.getSchoolLeaderboard(anyLong(), any(), anyLong(), any()))
                .thenAnswer(inv -> page);

        mockMvc.perform(get("/api/v1/gamification/leaderboard/school/1")
                        .with(SecurityTestUtils.withUser(1L, RoleName.STUDENT)))
                .andExpect(status().isOk());
    }
}
