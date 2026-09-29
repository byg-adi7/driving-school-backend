package com.drivingschool.backend.sms;

import com.drivingschool.backend.config.TwilioConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Thin HTTP client for Twilio Verify (https://www.twilio.com/docs/verify/api), same
 * direct-HTTPS approach as TwilioSmsClient. Verify generates the code, sends it through
 * the WhatsApp sender configured on the Verify service, and checks it - the code never
 * passes through this application.
 */
@Component
@RequiredArgsConstructor
public class TwilioVerifyClient {

    private static final String VERIFICATIONS_URL = "https://verify.twilio.com/v2/Services/%s/Verifications";
    private static final String VERIFICATION_CHECK_URL = "https://verify.twilio.com/v2/Services/%s/VerificationCheck";

    private final TwilioConfig config;
    private final RestTemplate restTemplate;

    /** Sends a new code over WhatsApp to an E.164 number. Throws on failure. */
    public void sendWhatsAppCode(String toNumber) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", toNumber);
        form.add("Channel", "whatsapp");
        restTemplate.postForObject(String.format(VERIFICATIONS_URL, config.getVerifyServiceSid()),
                new HttpEntity<>(form, headers()), Map.class);
    }

    /**
     * True only when Twilio reports the code as "approved". Twilio answers 404 when there
     * is no pending verification for the number any more (expired, already approved, or
     * too many attempts) - that is a wrong/dead code, not an error. Anything else throws.
     */
    public boolean checkCode(String toNumber, String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", toNumber);
        form.add("Code", code);
        try {
            Map<?, ?> response = restTemplate.postForObject(
                    String.format(VERIFICATION_CHECK_URL, config.getVerifyServiceSid()),
                    new HttpEntity<>(form, headers()), Map.class);
            return response != null && "approved".equals(response.get("status"));
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            throw ex;
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(config.getAccountSid(), config.getAuthToken());
        return headers;
    }
}
