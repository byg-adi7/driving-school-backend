package com.drivingschool.backend.integration;

import com.drivingschool.backend.auth.dto.AuthResponse;
import com.drivingschool.backend.auth.dto.LoginRequest;
import com.drivingschool.backend.auth.dto.RefreshTokenRequest;
import com.drivingschool.backend.auth.dto.RegisterRequest;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.dto.SchoolResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full auth lifecycle against a real (Testcontainers-backed) Postgres + Redis stack,
 * through the actual HTTP layer with the full filter chain (JWT auth, rate limiting)
 * active - proves the whole chain works together, not just each piece in isolation.
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void fullLifecycle_adminRegistersInstructor_instructorLogsInRefreshesAndLogsOut() throws Exception {
        String adminToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        SchoolResponse school = createSchool(adminToken, "Auth Lifecycle Driving School");

        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("instructor.lifecycle@example.com")
                .password("SecurePass123!")
                .firstName("Ian")
                .lastName("Instructor")
                .schoolId(school.getId())
                .role(RoleName.INSTRUCTOR)
                .licenseNumber("LIC-LIFECYCLE-1")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        AuthResponse instructorAuth = loginFull("instructor.lifecycle@example.com", "SecurePass123!");
        assertThat(instructorAuth.getAccessToken()).isNotBlank();
        assertThat(instructorAuth.getRefreshToken()).isNotBlank();
        assertThat(instructorAuth.getUser().getRoles()).containsExactly("INSTRUCTOR");

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RefreshTokenRequest.builder().refreshToken(instructorAuth.getRefreshToken()).build())))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse refreshed = parse(refreshResult, AuthResponse.class);
        assertThat(refreshed.getAccessToken()).isNotBlank();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", bearer(refreshed.getAccessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RefreshTokenRequest.builder().refreshToken(refreshed.getRefreshToken()).build())))
                .andExpect(status().isOk());

        // the revoked refresh token must no longer work
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RefreshTokenRequest.builder().refreshToken(refreshed.getRefreshToken()).build())))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void publicSelfRegistration_isRejectedWithoutAuthentication() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("uninvited@example.com")
                .password("SecurePass123!")
                .firstName("No")
                .lastName("Invite")
                .schoolId(1L)
                .role(RoleName.STUDENT)
                .build();

        // Anonymous requests fail the @PreAuthorize role check on this endpoint; whether
        // Spring Security maps that to 401 or 403 depends on entry-point configuration,
        // so this only asserts "rejected," not the exact status code.
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void login_withWrongPassword_isRejected() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email(BOOTSTRAP_ADMIN_EMAIL)
                .password("definitely-wrong-password")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }
}
