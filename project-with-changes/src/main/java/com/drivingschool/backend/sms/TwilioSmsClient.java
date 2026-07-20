package com.drivingschool.backend.sms;

import com.drivingschool.backend.config.TwilioConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * Thin HTTP client for Twilio's REST API, mirroring how ResendEmailClient
 * talks to Resend: a direct HTTPS call instead of pulling in Twilio's full
 * Java SDK, since this only ever needs to send a single text message.
 */
@Component
@RequiredArgsConstructor
public class TwilioSmsClient {

    private static final String MESSAGES_URL_TEMPLATE = "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";

    private final TwilioConfig config;
    private final RestTemplate restTemplate;

    /**
     * Sends a plain-text SMS. Throws on failure (network error, invalid
     * credentials, unverified/invalid destination number, etc.) - callers are
     * responsible for catching and handling that the same way they already
     * treat any other send failure (logged, not fatal to the caller's own
     * operation).
     */
    public void send(String toNumber, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(config.getAccountSid(), config.getAuthToken());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", toNumber);
        form.add("From", config.getFromNumber());
        form.add("Body", body);

        String url = String.format(MESSAGES_URL_TEMPLATE, config.getAccountSid());
        restTemplate.postForObject(url, new HttpEntity<>(form, headers), String.class);
    }
}
