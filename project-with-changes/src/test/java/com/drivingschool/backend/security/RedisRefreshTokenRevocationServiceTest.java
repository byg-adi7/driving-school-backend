package com.drivingschool.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenRevocationServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisRefreshTokenRevocationService revocationService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        revocationService = new RedisRefreshTokenRevocationService(redisTemplate);
    }

    @Test
    void revoke_withPositiveRemainingMs_setsKeyWithMatchingTtl() {
        revocationService.revoke("jti-1", 5000L);

        verify(valueOperations).set(eq("revoked:refresh:jti-1"), eq("1"), eq(Duration.ofMillis(5000L)));
    }

    @Test
    void revoke_withNonPositiveRemainingMs_doesNothing() {
        revocationService.revoke("jti-1", 0L);
        revocationService.revoke("jti-2", -100L);

        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void isRevoked_whenKeyExists_returnsTrue() {
        when(redisTemplate.hasKey("revoked:refresh:jti-1")).thenReturn(true);

        assertThat(revocationService.isRevoked("jti-1")).isTrue();
    }

    @Test
    void isRevoked_whenKeyMissing_returnsFalse() {
        when(redisTemplate.hasKey("revoked:refresh:jti-1")).thenReturn(false);

        assertThat(revocationService.isRevoked("jti-1")).isFalse();
    }
}
