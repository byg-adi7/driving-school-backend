package com.drivingschool.backend.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Fixed-window counter backed by Redis: INCR the window's key, set its expiry
 * only on the first hit so the window resets itself with no cleanup job, and
 * compare against the limit. Shared across all app instances, unlike an
 * in-memory bucket, so the limit is actually per-client rather than "whoever
 * happens to hit this JVM".
 */
@Service
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitResult tryConsume(String key, int limit, Duration window) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            count = 1L;
        }
        if (count == 1L) {
            redisTemplate.expire(key, window);
        }

        if (count > limit) {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            long retryAfterSeconds = (ttl != null && ttl > 0) ? ttl : window.toSeconds();
            return new RateLimitResult(false, 0, retryAfterSeconds);
        }

        return new RateLimitResult(true, limit - count, 0);
    }
}
