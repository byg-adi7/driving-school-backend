package com.drivingschool.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application against a genuinely fresh Postgres database
 * (no active Spring profile, so {@code ddl-auto=validate} applies, same as
 * production) so that a Flyway migration drifting from the JPA entities -
 * exactly the V8/V9 bug fixed in this repo's history - fails this test
 * instead of only surfacing on a real production deploy.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "app.jwt.secret=schema-validation-test-secret-at-least-32-chars",
                "app.bootstrap.admin.password=schema-validation-test-password"
        })
class SchemaValidationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoadsAgainstFreshlyMigratedDatabase() {
        // Intentionally empty: if Flyway migration fails, or Hibernate's
        // ddl-auto=validate finds the resulting schema doesn't match the JPA
        // entities, the context fails to start and this test fails.
    }
}
