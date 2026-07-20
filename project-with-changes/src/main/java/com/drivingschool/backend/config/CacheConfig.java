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
 * building a fresh one from scratch.
 *
 * Default typing is deliberately left OFF for the shared default config used by
 * every List-returning @Cacheable method (getPublished(), getAllActive(),
 * findAll(), etc). This looks broken - deserializing without type info gives
 * back a List<LinkedHashMap>, not the real DTO - but for a purely List<T>
 * return type, that never actually surfaces: generics are erased, so the cast
 * back to List<T> at the call site can't fail the way it can for a
 * non-generic return type, and a LinkedHashMap with the same field names
 * serializes to byte-identical JSON as the real DTO, so the eventual HTTP
 * response is indistinguishable either way.
 *
 * That masking does NOT hold for a single-object return type like
 * SchoolServiceImpl.getById(): School getById(...) has a concrete,
 * non-erased return type, so a cache hit that comes back as a LinkedHashMap
 * throws a real ClassCastException at the @Cacheable proxy boundary the
 * moment two different callers populate/reuse that entry. This bit the
 * "schools" cache specifically once getAllActive() was split out below - the
 * cache name used to hold both a single SchoolResponse (getById) and a
 * List<SchoolResponse> (getAllActive) at once, so it couldn't safely take
 * default typing (Jackson has no clean way to type-tag a root-level List the
 * same way it tags an object - GenericJackson2JsonRedisSerializer.builder()
 * .defaultTyping(true) hits the exact same root-level-array failure as
 * ObjectMapper.activateDefaultTyping(..., As.PROPERTY) does by hand; this was
 * confirmed via a live reproduction against the actual library classes, not
 * assumed). Splitting getAllActive() onto its own "schools-active" cache name
 * means "schools" now only ever holds single SchoolResponse values, so it's
 * safe to give it real default typing and fix the ClassCastException
 * properly instead of working around it.
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

        // "schools" only ever caches a single SchoolResponse (getById) now, so
        // real default typing is safe here and fixes the LinkedHashMap
        // ClassCastException instead of just avoiding it.
        RedisCacheConfiguration schoolsConfig = defaultConfig.serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(GenericJackson2JsonRedisSerializer.builder()
                        .objectMapper(appObjectMapper.copy())
                        .defaultTyping(true)
                        .build()));

        return builder -> builder
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("roles", defaultConfig.entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration("schools", schoolsConfig);
    }
}
