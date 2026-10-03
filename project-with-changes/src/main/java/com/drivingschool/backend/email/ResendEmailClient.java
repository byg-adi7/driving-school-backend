package com.drivingschool.backend.email;

import com.drivingschool.backend.config.ResendConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
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
    private static final String RESEND_BATCH_API_URL = "https://api.resend.com/emails/batch";

    /** Resend's documented maximum number of emails per batch request. */
    public static final int MAX_BATCH_SIZE = 100;

    /** One email in a batch - each goes to exactly one recipient, so nobody sees the others' addresses. */
    public record BatchEmail(String toAddress, String subject, String textBody) {
    }

    private final ResendConfig config;
    private final RestTemplate restTemplate;

    /**
     * Sends a plain-text email. Throws on failure (network error, invalid API
     * key, unverified sending domain, etc.) - callers are responsible for
     * catching and handling that the same way they already treat any other
     * send failure (logged, not fatal to the caller's own operation).
     */
    /** Sends a message with both an HTML version (e.g. a button) and a plain-text fallback. */
    public void send(String fromAddress, String toAddress, String subject, String textBody, String htmlBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        Map<String, Object> body = Map.of(
                "from", fromAddress,
                "to", toAddress,
                "subject", subject,
                "text", textBody,
                "html", htmlBody);

        restTemplate.postForObject(RESEND_API_URL, new HttpEntity<>(body, headers), String.class);
    }

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

    /**
     * Sends up to {@link #MAX_BATCH_SIZE} emails in ONE request (POST /emails/batch).
     * For fan-out like school announcements: Resend's default rate limit is 10 requests
     * per second per team, so one request per recipient would start failing with 429 at
     * a normal school size. Throws on failure, same contract as {@link #send}.
     */
    public void sendBatch(String fromAddress, List<BatchEmail> emails) {
        if (emails.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Resend accepts at most " + MAX_BATCH_SIZE + " emails per batch, got " + emails.size());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        List<Map<String, Object>> body = emails.stream()
                .map(email -> Map.<String, Object>of(
                        "from", fromAddress,
                        "to", email.toAddress(),
                        "subject", email.subject(),
                        "text", email.textBody()))
                .toList();

        restTemplate.postForObject(RESEND_BATCH_API_URL, new HttpEntity<>(body, headers), String.class);
    }
}
