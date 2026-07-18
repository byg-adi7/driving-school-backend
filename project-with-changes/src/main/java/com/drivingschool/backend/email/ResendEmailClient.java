package com.drivingschool.backend.email;

import com.drivingschool.backend.config.ResendConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Shared HTTP client for Resend (https://resend.com), used by both
 * EmailService (password-reset emails) and EmailNotificationSender (the
 * EMAIL notification channel). Sends over plain HTTPS rather than raw SMTP -
 * Railway blocks outbound SMTP entirely on non-Pro plans (and recommends an
 * HTTPS-API email provider regardless of plan), which is what made the
 * previous JavaMailSender-based implementation unusable in production.
 */
@Component
@RequiredArgsConstructor
public class ResendEmailClient {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final ResendConfig config;
    private final RestTemplate restTemplate;

    /**
     * Sends a plain-text email. Throws on failure (network error, invalid API
     * key, unverified sending domain, etc.) - callers are responsible for
     * catching and handling that the same way they already treat any other
     * send failure (logged, not fatal to the caller's own operation).
     */
    public void send(String fromAddress, String toAddress, String subject, String textBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        Map<String, Object> body = Map.of(
                "from", fromAddress,
                "to", toAddress,
                "subject", subject,
                "text", textBody);

        restTemplate.postForObject(RESEND_API_URL, new HttpEntity<>(body, headers), String.class);
    }
}
