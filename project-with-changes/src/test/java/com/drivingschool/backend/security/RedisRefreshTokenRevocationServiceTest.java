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

    @Test
    void revokeAllForUser_storesTheCurrentSecondWithTheGivenTtl() {
        long before = System.currentTimeMillis() / 1000;

        revocationService.revokeAllForUser(7L, 604_800_000L);

        org.mockito.ArgumentCaptor<String> value = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(valueOperations).set(eq("revoked:refresh:user:7"), value.capture(), eq(Duration.ofMillis(604_800_000L)));
        assertThat(Long.parseLong(value.getValue())).isBetween(before, System.currentTimeMillis() / 1000);
    }

    @Test
    void isRevokedForUser_tokenIssuedBeforeTheMarker_isRevoked() {
        org.mockito.Mockito.when(valueOperations.get("revoked:refresh:user:7")).thenReturn("1000");

        assertThat(revocationService.isRevokedForUser(7L, 999L)).isTrue();
    }

    @Test
    void isRevokedForUser_tokenIssuedInTheSameSecondOrLater_isNotRevoked() {
        org.mockito.Mockito.when(valueOperations.get("revoked:refresh:user:7")).thenReturn("1000");

        assertThat(revocationService.isRevokedForUser(7L, 1000L)).isFalse();
        assertThat(revocationService.isRevokedForUser(7L, 1001L)).isFalse();
    }

    @Test
    void isRevokedForUser_noMarker_isNotRevoked() {
        org.mockito.Mockito.when(valueOperations.get("revoked:refresh:user:7")).thenReturn(null);

        assertThat(revocationService.isRevokedForUser(7L, 1L)).isFalse();
    }
}
