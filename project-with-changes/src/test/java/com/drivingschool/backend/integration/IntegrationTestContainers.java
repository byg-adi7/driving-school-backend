package com.drivingschool.backend.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The one Postgres + Redis pair every integration test shares, started once per JVM
 * (the Testcontainers "singleton container" pattern). Shared by AbstractIntegrationTest
 * (MockMvc, rolled-back transactions) and the real-server tests that need an actual
 * port - e.g. RealtimeWebSocketIntegrationTest - so neither starts its own pair.
 */
final class IntegrationTestContainers {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    private IntegrationTestContainers() {
    }

    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
