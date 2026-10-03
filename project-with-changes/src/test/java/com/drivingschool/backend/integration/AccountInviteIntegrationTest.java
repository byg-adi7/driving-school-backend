package com.drivingschool.backend.integration;

import com.drivingschool.backend.email.ResendEmailClient;
import com.drivingschool.backend.school.dto.SchoolResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The invite flow over HTTP + Postgres: an admin creates a student without a password,
 * the student can't sign in yet, the link greets them, they choose a password and are
 * signed in, and the link can't be used again. Only the outbound email is mocked.
 */
@TestPropertySource(properties = "resend.apiKey=re_integration_test")
class AccountInviteIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean private ResendEmailClient resendEmailClient;

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResultActions actions) throws Exception {
        return (Map<String, Object>) objectMapper.readValue(actions.andReturn().getResponse().getContentAsString(), Map.class).get("data");
    }

    private ResultActions postJson(String path, String token, Object body) throws Exception {
        var request = post("/api/v1" + path).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", bearer(token));
        }
        return mockMvc.perform(request);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aStudentCreatedWithoutAPassword_setsTheirOwnFromTheInviteLink() throws Exception {
        String admin = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        SchoolResponse school = createSchool(admin, "Invite School");

        Map<String, Object> created = data(postJson("/auth/register", admin, Map.of(
                "email", "invited.student@example.com", "firstName", "Ransford", "lastName", "Adi",
                "schoolId", school.getId(), "role", "STUDENT")).andExpect(status().isCreated()));
        Map<String, Object> invite = (Map<String, Object>) created.get("invite");
        assertThat(invite.get("status")).isEqualTo("SENT");
        String url = (String) invite.get("url");
        String token = url.substring(url.indexOf("token=") + 6);

        // Not set up yet: told what to do.
        postJson("/auth/login", null, Map.of("email", "invited.student@example.com", "password", "anything123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Your account isn't set up yet. Open the invite link in your email to choose a password."));

        mockMvc.perform(get("/api/v1/auth/invite/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Ransford"))
                .andExpect(jsonPath("$.data.email").value("i***@example.com"))
                .andExpect(jsonPath("$.data.schoolName").value("Invite School"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"));

        Map<String, Object> signedIn = data(postJson("/auth/invite/accept", null,
                Map.of("token", token, "password", "MyOwnPass123")).andExpect(status().isOk()));
        assertThat(signedIn.get("accessToken")).isNotNull();

        // Single use, and their own password now works with no verification code.
        postJson("/auth/invite/accept", null, Map.of("token", token, "password", "Another123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This invite link has already been used. Sign in instead."));
        Map<String, Object> login = data(postJson("/auth/login", null,
                Map.of("email", "invited.student@example.com", "password", "MyOwnPass123")).andExpect(status().isOk()));
        assertThat(login.get("accessToken")).isNotNull();

        mockMvc.perform(get("/api/v1/auth/invite/not-a-real-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This invite link isn't valid."));
    }
}
