package com.drivingschool.backend.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base for tests that need a real server and real, committed transactions - the
 * counterpart to AbstractIntegrationTest, whose per-test rolled-back transaction means
 * nothing ever commits, so anything done "after commit" (realtime pushes, best-effort
 * notifications, async email) never happens there.
 *
 * Data committed here stays in the shared containers, so every account and school is
 * created with a unique suffix.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.jwt.secret=integration-test-secret-at-least-32-characters-long",
                "app.bootstrap.admin.password=integration-test-admin-password"
        })
abstract class AbstractRealServerIntegrationTest {

    protected static final String PASSWORD = "SecurePass123!";

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        IntegrationTestContainers.register(registry);
    }

    @LocalServerPort protected int port;
    @Autowired protected TestRestTemplate rest;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void flushRateLimitState() {
        // Rate-limit counters live in Redis and are shared with the other integration tests.
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> exchange(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<Map> response = rest.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).as("%s %s -> %s", method, path, response.getStatusCode()).isTrue();
        return (Map<String, Object>) response.getBody().get("data");
    }

    protected Map<String, Object> post(String path, String token, Object body) {
        return exchange(HttpMethod.POST, path, token, body);
    }

    protected Map<String, Object> getData(String path, String token) {
        return exchange(HttpMethod.GET, path, token, null);
    }

    // Accounts made by these tests are marked verified first - see AbstractIntegrationTest.loginFull.
    protected String login(String email, String password) {
        jdbcTemplate.update("UPDATE users SET email_verified = TRUE WHERE email = ?", email);
        return (String) post("/auth/login", null, Map.of("email", email, "password", password)).get("accessToken");
    }

    protected String loginAsBootstrapAdmin() {
        return login("admin@drivingschool.local", "integration-test-admin-password");
    }

    @SuppressWarnings("unchecked")
    protected Object createSchool(String bootstrapToken, String suffix) {
        Map<String, Object> created = post("/schools", bootstrapToken, Map.of(
                "schoolName", "Real Server School " + suffix,
                "schoolAddress", "1 Test Street",
                "adminEmail", "rs.owner." + suffix + "@example.com",
                "adminPassword", PASSWORD));
        return ((Map<String, Object>) created.get("school")).get("id");
    }

    protected void register(String bootstrapToken, Object schoolId, String role, String email) {
        post("/auth/register", bootstrapToken, role.equals("INSTRUCTOR")
                ? Map.of("email", email, "password", PASSWORD, "firstName", "Ina", "lastName", "Instructor",
                        "schoolId", schoolId, "role", role, "licenseNumber", "LIC-" + email.hashCode())
                : Map.of("email", email, "password", PASSWORD, "firstName", "Sam", "lastName", "Student",
                        "schoolId", schoolId, "role", role));
    }
}
