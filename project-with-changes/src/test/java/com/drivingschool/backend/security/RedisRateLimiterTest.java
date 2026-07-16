package com.drivingschool.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiter = new RedisRateLimiter(redisTemplate);
    }

    @Test
    void tryConsume_firstHitInWindow_setsExpiryAndAllows() {
        when(valueOperations.increment("key")).thenReturn(1L);

        RateLimiter.RateLimitResult result = rateLimiter.tryConsume("key", 10, Duration.ofMinutes(1));

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(9);
        verify(redisTemplate).expire("key", Duration.ofMinutes(1));
    }

    @Test
    void tryConsume_subsequentHitUnderLimit_doesNotResetExpiry() {
        when(valueOperations.increment("key")).thenReturn(5L);

        RateLimiter.RateLimitResult result = rateLimiter.tryConsume("key", 10, Duration.ofMinutes(1));

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(5);
        verify(redisTemplate, never()).expire("key", Duration.ofMinutes(1));
    }

    @Test
    void tryConsume_atLimit_isAllowed() {
        when(valueOperations.increment("key")).thenReturn(10L);

        RateLimiter.RateLimitResult result = rateLimiter.tryConsume("key", 10, Duration.ofMinutes(1));

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isZero();
    }

    @Test
    void tryConsume_overLimit_isDeniedWithRetryAfterFromTtl() {
        when(valueOperations.increment("key")).thenReturn(11L);
        when(redisTemplate.getExpire("key", TimeUnit.SECONDS)).thenReturn(37L);

        RateLimiter.RateLimitResult result = rateLimiter.tryConsume("key", 10, Duration.ofMinutes(1));

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isEqualTo(37L);
    }

    @Test
    void tryConsume_overLimitWithMissingTtl_fallsBackToWindowLength() {
        when(valueOperations.increment("key")).thenReturn(11L);
        when(redisTemplate.getExpire("key", TimeUnit.SECONDS)).thenReturn(null);

        RateLimiter.RateLimitResult result = rateLimiter.tryConsume("key", 10, Duration.ofMinutes(1));

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isEqualTo(60L);
    }
}
