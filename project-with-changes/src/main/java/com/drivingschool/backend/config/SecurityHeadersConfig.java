package com.drivingschool.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import java.util.Set;

/**
 * Security headers and request logging configuration for production
 */
@Configuration
public class SecurityHeadersConfig {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "set-cookie", "proxy-authorization", "x-api-key");

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter loggingFilter = new CommonsRequestLoggingFilter();
        loggingFilter.setIncludeClientInfo(true);
        loggingFilter.setIncludeQueryString(true);
        loggingFilter.setIncludePayload(false);  // Don't log payload for security
        loggingFilter.setMaxPayloadLength(10000);
        loggingFilter.setIncludeHeaders(true);
        loggingFilter.setHeaderPredicate(header -> !SENSITIVE_HEADERS.contains(header.toLowerCase()));
        loggingFilter.setAfterMessagePrefix("REQUEST DATA : ");
        return loggingFilter;
    }
}
