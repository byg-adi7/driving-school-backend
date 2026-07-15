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
    private long refreshTokenExpirationMs;

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
