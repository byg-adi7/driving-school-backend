package com.drivingschool.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis already backs rate limiting and refresh-token revocation, so it's reused
 * here as the cache store rather than adding a second piece of infrastructure.
 * A short default TTL (not just event-driven @CacheEvict) is a deliberate safety
 * net against any mutation path that forgets to evict.
 *
 * The cache serializer copies the app's own auto-configured ObjectMapper (same
 * JavaTimeModule/ParameterNamesModule registrations already proven to correctly
 * (de)serialize these Lombok @Builder response DTOs elsewhere) rather than
 * building a fresh one from scratch. Default typing is deliberately NOT
 * configured by hand here (no activateDefaultTyping call) - passing the plain
 * copy straight to `new GenericJackson2JsonRedisSerializer(mapper)` lets its
 * constructor apply Spring Data Redis's own TypeResolverBuilder.forEverything(),
 * which (unlike calling ObjectMapper.activateDefaultTyping(..., As.PROPERTY) or
 * As.WRAPPER_ARRAY directly - both tried and both confirmed broken via a live
 * reproduction) correctly round-trips both single-object AND root-level List
 * return values (getPublished(), getAllActive(), findAll(), etc. all cache a
 * List). Hand-rolling default typing here was the original bug.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(ObjectMapper appObjectMapper) {
        ObjectMapper cacheObjectMapper = appObjectMapper.copy();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheObjectMapper)));

        return builder -> builder
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("roles", defaultConfig.entryTtl(Duration.ofHours(1)));
    }
}
