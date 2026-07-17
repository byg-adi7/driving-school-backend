package com.drivingschool.backend.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
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
 * building a fresh one from scratch, and only adds default-typing to the copy -
 * the shared app-wide bean is left untouched since default typing is a
 * generic-Object-cache concern, not something request/response JSON needs.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(ObjectMapper appObjectMapper) {
        ObjectMapper cacheObjectMapper = appObjectMapper.copy();
        cacheObjectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

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
