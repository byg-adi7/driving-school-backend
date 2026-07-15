package com.drivingschool.backend.lesson.route.controller;

import com.drivingschool.backend.lesson.route.dto.GenerateRouteRequest;
import com.drivingschool.backend.lesson.route.dto.RouteResponse;
import com.drivingschool.backend.lesson.route.service.PracticalLessonRouteService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PracticalLessonRouteController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(PracticalLessonRouteControllerSecurityTest.MethodSecurityTestConfig.class)
class PracticalLessonRouteControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private PracticalLessonRouteService routeService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private GenerateRouteRequest validRequest() {
        GenerateRouteRequest request = new GenerateRouteRequest();
        request.setLiveSessionId(1L);
        request.setStartLocation("123 Main St");
        request.setDestinationLocation("456 Oak Ave");
        request.setStartLatitude(51.5);
        request.setStartLongitude(-0.1);
        request.setDestinationLatitude(51.6);
        request.setDestinationLongitude(-0.2);
        return request;
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void generateRoute_asStudent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/lesson-routes/generate").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void generateRoute_asInstructor_isOk() throws Exception {
        when(routeService.generateRoute(any(), anyLong())).thenReturn(RouteResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/lesson-routes/generate")
                        .with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.INSTRUCTOR))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void getInstructorRoutes_asStudent_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/lesson-routes/instructor/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "INSTRUCTOR")
    void getAllRoutes_asInstructor_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/lesson-routes"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void deleteRoute_asStudent_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/lesson-routes/1").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
