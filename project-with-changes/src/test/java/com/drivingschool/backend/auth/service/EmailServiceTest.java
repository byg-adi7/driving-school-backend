package com.drivingschool.backend.auth.service;

import com.drivingschool.backend.email.ResendEmailClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private ResendEmailClient resendEmailClient;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(resendEmailClient, "no-reply@drivingschool.local",
                "http://localhost:3000/reset-password");
    }

    @Test
    void sendPasswordResetEmail_sendsWithTokenLinkAndExpiryInBody() {
        emailService.sendPasswordResetEmail("student@example.com", "abc-token-123", 60);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(resendEmailClient).send(
                org.mockito.ArgumentMatchers.eq("no-reply@drivingschool.local"),
                org.mockito.ArgumentMatchers.eq("student@example.com"),
                org.mockito.ArgumentMatchers.eq("Reset your password"),
                bodyCaptor.capture());

        String body = bodyCaptor.getValue();
        assertThat(body).contains("http://localhost:3000/reset-password?token=abc-token-123");
        assertThat(body).contains("60 minutes");
    }

    @Test
    void sendPasswordResetEmail_whenClientThrows_doesNotPropagate() {
        doThrow(new RestClientException("resend unreachable"))
                .when(resendEmailClient).send(anyString(), anyString(), anyString(), anyString());

        // Must not throw - forgot-password always reports success regardless
        // of delivery outcome, so a mail-provider failure can never surface
        // to the caller.
        emailService.sendPasswordResetEmail("student@example.com", "abc-token-123", 60);

        verify(resendEmailClient, times(1)).send(anyString(), anyString(), anyString(), anyString());
    }
}
