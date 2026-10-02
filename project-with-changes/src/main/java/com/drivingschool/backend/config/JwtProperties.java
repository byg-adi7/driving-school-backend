package com.drivingschool.backend.config;

import com.drivingschool.backend.common.exception.BadRequestException;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret;
    private long accessTokenExpirationMs;
    /** Longest a "remember me" session can last (7 days by default). */
    private long refreshTokenExpirationMs;
    /** A session not refreshed for this long ends (2 hours) - the inactivity timeout. */
    private long idleTimeoutMs = 2 * 60 * 60 * 1000L;
    /** Longest a session without "remember me" can last (12 hours). */
    private long sessionMaxMs = 12 * 60 * 60 * 1000L;
    /** Admins can delete schools and accounts: their sessions never outlast this (1 day). */
    private long adminSessionMaxMs = 24 * 60 * 60 * 1000L;

    /**
     * Validate JWT configuration on initialization
     */
    @PostConstruct
    public void validate() {
        if (secret == null || secret.isEmpty()) {
            throw new BadRequestException("JWT secret is not configured");
        }
        if (secret.length() < 32) {
            throw new BadRequestException("JWT secret must be at least 32 characters long");
        }
        if (accessTokenExpirationMs <= 0) {
            throw new BadRequestException("Access token expiration must be greater than 0");
        }
        if (refreshTokenExpirationMs <= 0) {
            throw new BadRequestException("Refresh token expiration must be greater than 0");
        }
        if (refreshTokenExpirationMs <= accessTokenExpirationMs) {
            throw new BadRequestException("Refresh token expiration must be greater than access token expiration");
        }
    }
}
