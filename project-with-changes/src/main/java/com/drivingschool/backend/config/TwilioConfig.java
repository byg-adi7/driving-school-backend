package com.drivingschool.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "twilio")
@Data
public class TwilioConfig {

    /** No fallback default - a real credential, read exclusively from TWILIO_ACCOUNT_SID. */
    private String accountSid;

    /** No fallback default - a real credential, read exclusively from TWILIO_AUTH_TOKEN. */
    private String authToken;

    /** The Twilio phone number messages are sent from, read from TWILIO_FROM_NUMBER. */
    private String fromNumber;

    /**
     * The Twilio Verify service (VA...) that sends and checks WhatsApp verification
     * codes, read from TWILIO_VERIFY_SERVICE_SID. Verify sends from the WhatsApp
     * sender configured on that service, so it doesn't need fromNumber.
     */
    private String verifyServiceSid;

    public boolean isConfigured() {
        return accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank()
                && fromNumber != null && !fromNumber.isBlank();
    }

    public boolean isVerifyConfigured() {
        return accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank()
                && verifyServiceSid != null && !verifyServiceSid.isBlank();
    }
}
