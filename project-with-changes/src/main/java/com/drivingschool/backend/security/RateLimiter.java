package com.drivingschool.backend.security;

import java.time.Duration;

public interface RateLimiter {

    /**
     * Attempts to consume one unit from the fixed window identified by {@code key}.
     * Callers must scope {@code key} to the client being limited (e.g. an IP
     * address) - this method has no notion of "who" on its own.
     */
    RateLimitResult tryConsume(String key, int limit, Duration window);

    record RateLimitResult(boolean allowed, long remaining, long retryAfterSeconds) {
    }
}
