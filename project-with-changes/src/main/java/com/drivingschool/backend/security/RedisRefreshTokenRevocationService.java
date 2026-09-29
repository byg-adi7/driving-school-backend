package com.drivingschool.backend.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class RedisRefreshTokenRevocationService implements RefreshTokenRevocationService {

    private static final String KEY_PREFIX = "revoked:refresh:";
    private static final String USER_KEY_PREFIX = "revoked:refresh:user:";

    private final StringRedisTemplate redisTemplate;

    public RedisRefreshTokenRevocationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void revoke(String jti, long remainingMs) {
        if (remainingMs <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", Duration.ofMillis(remainingMs));
    }

    @Override
    public boolean isRevoked(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }

    @Override
    public void revokeAllForUser(Long userId, long ttlMs) {
        long nowEpochSeconds = System.currentTimeMillis() / 1000;
        redisTemplate.opsForValue().set(USER_KEY_PREFIX + userId, Long.toString(nowEpochSeconds), Duration.ofMillis(ttlMs));
    }

    // Strictly-before, in whole seconds: a token issued in the same second as the
    // revocation (e.g. the user's own fresh login right after resetting) survives.
    @Override
    public boolean isRevokedForUser(Long userId, long issuedAtEpochSeconds) {
        String revokedBefore = redisTemplate.opsForValue().get(USER_KEY_PREFIX + userId);
        return revokedBefore != null && issuedAtEpochSeconds < Long.parseLong(revokedBefore);
    }
}
