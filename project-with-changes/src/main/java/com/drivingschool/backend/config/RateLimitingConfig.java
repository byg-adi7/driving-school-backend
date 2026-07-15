package com.drivingschool.backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

/**
 * Rate limiting configuration using Bucket4j
 * Define rate limits for different endpoints
 */
@Configuration
public class RateLimitingConfig {

    /**
     * Create a bucket for authentication endpoints
     * 10 requests per minute
     */
    @Bean(name = "authBucket")
    @Profile("!test")
    public Bucket authBucket() {
        Bandwidth limit = Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)));
        return Bucket4j.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Create a bucket for general API endpoints
     * 100 requests per minute
     */
    @Bean(name = "apiRateLimiter")
    @Profile("!test")
    public Bucket apiRateLimiter() {
        Bandwidth limit = Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1)));
        return Bucket4j.builder()
                .addLimit(limit)
                .build();
    }
}
