package com.drivingschool.backend.learning.controller;

import com.drivingschool.backend.learning.dto.CreateResourceRequest;
import com.drivingschool.backend.learning.dto.ResourceResponse;
import com.drivingschool.backend.learning.enums.ResourceType;
import com.drivingschool.backend.learning.service.ResourceService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ResourceController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitingFilter.class, ApiVersioningFilter.class}))
@Import(ResourceControllerSecurityTest.MethodSecurityTestConfig.class)
class ResourceControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private ResourceService resourceService;

    @TestConfiguration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private CreateResourceRequest validCreateRequest() {
        return CreateResourceRequest.builder()
                .lessonId(1L).title("Handbook").fileUrl("https://example.com/f.pdf").type(ResourceType.PDF).build();
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void create_asStudent_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/resources").with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_asInstructor_isCreated() throws Exception {
        when(resourceService.create(any(), anyLong(), anyString()))
                .thenReturn(ResourceResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/v1/resources")
                        .with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.INSTRUCTOR))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validCreateRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void delete_asStudent_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/resources/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asInstructor_isOk() throws Exception {
        mockMvc.perform(delete("/api/v1/resources/1")
                        .with(csrf())
                        .with(SecurityTestUtils.withUser(1L, RoleName.INSTRUCTOR)))
                .andExpect(status().isOk());
    }

    @Test
    void getByLesson_asStudent_isOk() throws Exception {
        mockMvc.perform(get("/api/v1/resources/lesson/1")
                        .with(SecurityTestUtils.withUser(1L, RoleName.STUDENT)))
                .andExpect(status().isOk());
    }
}
