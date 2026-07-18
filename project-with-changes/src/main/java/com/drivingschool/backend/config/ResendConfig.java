package com.drivingschool.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "resend")
@Data
public class ResendConfig {

    /** No fallback default - a real credential, read exclusively from RESEND_API_KEY. */
    private String apiKey;
}
