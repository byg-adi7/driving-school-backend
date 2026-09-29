package com.drivingschool.backend.integration;

import com.drivingschool.backend.auth.dto.AuthResponse;
import com.drivingschool.backend.auth.dto.CurrentUserResponse;
import com.drivingschool.backend.auth.dto.LoginRequest;
import com.drivingschool.backend.auth.dto.RegisterRequest;
import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.dto.CreateSchoolWithAdminRequest;
import com.drivingschool.backend.school.dto.SchoolResponse;
import com.drivingschool.backend.school.dto.SchoolWithAdminResponse;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared base for full-stack integration tests: boots the real Spring context
 * (no active profile, so ddl-auto=validate applies, same as prod) with the
 * complete servlet filter chain active (JWT auth, rate limiting) against
 * genuinely fresh Postgres and Redis containers - unlike @WebMvcTest
 * security-slice tests, nothing here is mocked.
 *
 * Containers are started once (IntegrationTestContainers - the Testcontainers
 * "singleton container" pattern) rather than per-class, so every integration
 * test class extending this one shares the same running Postgres/Redis and
 * the same cached Spring context, keeping the whole suite fast. Each test
 * method runs inside a rolled-back transaction so DB state never leaks
 * between tests; Redis (rate-limit counters) is not part of that transaction
 * and is flushed explicitly instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "app.jwt.secret=integration-test-secret-at-least-32-characters-long",
                "app.bootstrap.admin.password=integration-test-admin-password"
        })
@AutoConfigureMockMvc
@Transactional
public abstract class AbstractIntegrationTest {

    protected static final String BOOTSTRAP_ADMIN_EMAIL = "admin@drivingschool.local";
    protected static final String BOOTSTRAP_ADMIN_PASSWORD = "integration-test-admin-password";

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        IntegrationTestContainers.register(registry);
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redisTemplate;
    @PersistenceContext private EntityManager entityManager;

    /**
     * A whole test method shares ONE Hibernate persistence context (Spring's test-managed
     * transaction binds a single EntityManager for the method's duration, unlike real
     * production where every HTTP request gets its own) - so a DB-level ON DELETE CASCADE
     * (e.g. the school/admin cascade-delete) isn't reflected back into any Java entity
     * objects already resident in this session. Call this after such an operation and
     * before asserting on a subsequent read in the same test, or Hibernate's stale
     * first-level cache can return objects that no longer exist, or throw while
     * autoflushing pending state tied to rows the DB already removed out from under it.
     */
    protected void clearPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }

    @BeforeEach
    void flushRateLimitState() {
        // Rate limiting is Redis-backed and outside the test's rolled-back DB
        // transaction, so it isn't reset between tests the way DB state is -
        // flush it explicitly so one test's auth calls never exhaust another's budget.
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    protected <T> T parse(MvcResult result, Class<T> dataType) throws Exception {
        JavaType type = objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, dataType);
        ApiResponse<T> response = objectMapper.readValue(result.getResponse().getContentAsString(), type);
        return response.getData();
    }

    protected String login(String email, String password) throws Exception {
        return loginFull(email, password).getAccessToken();
    }

    protected AuthResponse loginFull(String email, String password) throws Exception {
        LoginRequest request = LoginRequest.builder().email(email).password(password).build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return parse(result, AuthResponse.class);
    }

    /**
     * Creates a school and its owning admin together (schools can't exist without
     * one) via the bootstrap admin's token, and returns just the school - callers
     * needing the owning admin's own credentials should call the underlying
     * endpoint directly instead.
     */
    protected SchoolResponse createSchool(String adminToken, String name) throws Exception {
        CreateSchoolWithAdminRequest request = CreateSchoolWithAdminRequest.builder()
                .schoolName(name)
                .schoolAddress("1 Test Street")
                .adminEmail("owner-" + java.util.UUID.randomUUID() + "@example.com")
                .adminPassword("SecurePass123!")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/schools")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return parse(result, SchoolWithAdminResponse.class).getSchool();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    /** A registered user's identity: User.id plus their own StudentProfile.id/InstructorProfile.id (whichever applies). */
    protected record Person(String email, String token, Long userId, Long profileId) {}

    /** Registers a user via the admin token, logs them in, and resolves their own profile id via GET /auth/me. */
    protected Person registerAndIdentify(String adminToken, Long schoolId, RoleName role, String email, String licenseNumber) throws Exception {
        RegisterRequest.RegisterRequestBuilder builder = RegisterRequest.builder()
                .email(email)
                .password("SecurePass123!")
                .firstName("Test")
                .lastName(role.name())
                .schoolId(schoolId)
                .role(role);
        if (licenseNumber != null) {
            builder.licenseNumber(licenseNumber);
        }

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(builder.build())))
                .andExpect(status().isCreated());

        String token = login(email, "SecurePass123!");

        MvcResult meResult = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        CurrentUserResponse me = parse(meResult, CurrentUserResponse.class);

        Long profileId = role == RoleName.STUDENT ? me.getStudentProfileId() : me.getInstructorProfileId();
        return new Person(email, token, me.getUserId(), profileId);
    }
}
