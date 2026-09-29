package com.drivingschool.backend.integration;

import com.drivingschool.backend.email.ResendEmailClient;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.dto.CreateSchoolWithAdminRequest;
import com.drivingschool.backend.school.dto.SchoolResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real one-time-code flow through HTTP, Postgres and Redis: login answers an
 * unverified account with a challenge (no tokens), a code is emailed, a wrong code and
 * an early resend are refused, the right code verifies the account and logs it in, and
 * later logins go straight through. Only the outbound Resend call is mocked, to read
 * the code. (WhatsApp isn't configured here, so only EMAIL is offered.)
 */
@TestPropertySource(properties = "resend.apiKey=re_integration_test")
class AccountVerificationIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SecurePass123!";

    @MockitoBean private ResendEmailClient resendEmailClient;

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        return (Map<String, Object>) objectMapper.readValue(result.getResponse().getContentAsString(), Map.class).get("data");
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post("/api/v1" + path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String emailedCode(String to) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(resendEmailClient).send(anyString(), eq(to), eq("Your Aidly verification code"), body.capture());
        Matcher matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(body.getValue());
        assertThat(matcher.find()).isTrue();
        clearInvocations(resendEmailClient);
        return matcher.group(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void newAccount_mustConfirmAnEmailedCode_beforeItGetsTokens() throws Exception {
        String bootstrapToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        SchoolResponse school = createSchool(bootstrapToken, "Verification School");
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", bearer(bootstrapToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "verify.student@example.com",
                                "password", PASSWORD, "firstName", "Vera", "lastName", "Student",
                                "schoolId", school.getId(), "role", RoleName.STUDENT.name()))))
                .andExpect(status().isCreated());

        // Right password, unverified account: a challenge, no tokens.
        Map<String, Object> login = data(postJson("/auth/login", Map.of("email", "verify.student@example.com", "password", PASSWORD))
                .andExpect(status().isOk()));
        assertThat(login).doesNotContainKey("accessToken");
        assertThat(login.get("verificationRequired")).isEqualTo(true);
        Map<String, Object> challenge = (Map<String, Object>) login.get("verification");
        assertThat((List<String>) challenge.get("channels")).containsExactly("EMAIL");
        assertThat(challenge.get("maskedEmail")).isEqualTo("v***@example.com");
        String challengeId = (String) challenge.get("challengeId");

        postJson("/auth/verification/send", Map.of("challengeId", challengeId, "channel", "EMAIL")).andExpect(status().isOk());
        String code = emailedCode("verify.student@example.com");

        postJson("/auth/verification/send", Map.of("challengeId", challengeId, "channel", "EMAIL"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        String wrong = code.equals("000000") ? "111111" : "000000";
        postJson("/auth/verification/confirm", Map.of("challengeId", challengeId, "code", wrong))
                .andExpect(status().isBadRequest());

        Map<String, Object> tokens = data(postJson("/auth/verification/confirm", Map.of("challengeId", challengeId, "code", code))
                .andExpect(status().isOk()));
        String accessToken = (String) tokens.get("accessToken");
        assertThat(accessToken).isNotBlank();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(accessToken))).andExpect(status().isOk());

        // The challenge is spent, and from now on login goes straight through.
        postJson("/auth/verification/confirm", Map.of("challengeId", challengeId, "code", code))
                .andExpect(status().isBadRequest());
        Map<String, Object> again = data(postJson("/auth/login", Map.of("email", "verify.student@example.com", "password", PASSWORD))
                .andExpect(status().isOk()));
        assertThat(again.get("accessToken")).isNotNull();
        assertThat(again).doesNotContainKey("verificationRequired");
    }

    @Test
    void aNewSchoolsOwningAdmin_alsoVerifiesAtFirstLogin_andAWrongPasswordNeverGetsAChallenge() throws Exception {
        String bootstrapToken = login(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        mockMvc.perform(post("/api/v1/schools")
                        .header("Authorization", bearer(bootstrapToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateSchoolWithAdminRequest.builder()
                                .schoolName("Verification Owner School").schoolAddress("1 Test Street")
                                .adminEmail("verify.owner@example.com").adminPassword(PASSWORD).build())))
                .andExpect(status().isCreated());

        postJson("/auth/login", Map.of("email", "verify.owner@example.com", "password", "wrong-password-123"))
                .andExpect(status().isUnauthorized());

        Map<String, Object> login = data(postJson("/auth/login", Map.of("email", "verify.owner@example.com", "password", PASSWORD))
                .andExpect(status().isOk()));
        assertThat(login.get("verificationRequired")).isEqualTo(true);
        assertThat(login).doesNotContainKey("accessToken");
    }
}
