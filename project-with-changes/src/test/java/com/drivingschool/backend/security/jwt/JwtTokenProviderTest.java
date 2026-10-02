package com.drivingschool.backend.security.jwt;

import com.drivingschool.backend.config.JwtProperties;
import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.security.UserPrincipal;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-at-least-32-characters-long-1234";

    private JwtTokenProvider tokenProvider;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setAccessTokenExpirationMs(900_000L);
        properties.setRefreshTokenExpirationMs(604_800_000L);
        tokenProvider = new JwtTokenProvider(properties);

        User user = User.builder()
                .email("student@example.com")
                .password("encoded")
                .enabled(true)
                .emailVerified(true)
                .build();
        ReflectionTestUtils.setField(user, "id", 42L);
        Role role = Role.builder().name(RoleName.STUDENT).build();
        user.addRole(role);
        principal = new UserPrincipal(user);
    }

    @Test
    void generateAccessToken_producesTokenReadableAsAccessToken() {
        String token = tokenProvider.generateAccessToken(principal);

        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.isAccessToken(token)).isTrue();
        assertThat(tokenProvider.isRefreshToken(token)).isFalse();
        assertThat(tokenProvider.getEmailFromToken(token)).isEqualTo("student@example.com");
        assertThat(tokenProvider.getUserIdFromToken(token)).isEqualTo(42L);
    }

    @Test
    void generateRefreshToken_producesTokenReadableAsRefreshToken() {
        String token = tokenProvider.generateRefreshToken(principal);

        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.isRefreshToken(token)).isTrue();
        assertThat(tokenProvider.isAccessToken(token)).isFalse();
    }

    @Test
    void validateToken_rejectsTokenSignedWithDifferentSecret() {
        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecret("a-completely-different-secret-key-1234567890");
        otherProperties.setAccessTokenExpirationMs(900_000L);
        otherProperties.setRefreshTokenExpirationMs(604_800_000L);
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherProperties);

        String token = otherProvider.generateAccessToken(principal);

        assertThat(tokenProvider.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_rejectsMalformedToken() {
        assertThat(tokenProvider.validateToken("not-a-jwt")).isFalse();
    }

    @Test
    void generateAccessToken_includesUniqueJti() {
        String token1 = tokenProvider.generateAccessToken(principal);
        String token2 = tokenProvider.generateAccessToken(principal);

        assertThat(tokenProvider.getJtiFromToken(token1)).isNotBlank();
        assertThat(tokenProvider.getJtiFromToken(token1)).isNotEqualTo(tokenProvider.getJtiFromToken(token2));
    }

    @Test
    void getExpirationFromToken_returnsFutureDateForFreshToken() {
        String token = tokenProvider.generateRefreshToken(principal);

        assertThat(tokenProvider.getExpirationFromToken(token)).isAfter(new java.util.Date());
    }

    @Test
    void validateToken_rejectsExpiredToken() throws InterruptedException {
        JwtProperties shortLivedProperties = new JwtProperties();
        shortLivedProperties.setSecret(SECRET);
        shortLivedProperties.setAccessTokenExpirationMs(1L);
        shortLivedProperties.setRefreshTokenExpirationMs(604_800_000L);
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(shortLivedProperties);

        String token = shortLivedProvider.generateAccessToken(principal);
        Thread.sleep(50);

        assertThat(shortLivedProvider.validateToken(token)).isFalse();
    }

    // --- sessions: idle timeout, maximum length, remember me ---

    private static final long HOUR = 3_600_000L;

    private long expiresInMs(String token) {
        return tokenProvider.getExpirationFromToken(token).getTime() - System.currentTimeMillis();
    }

    private UserPrincipal admin() {
        User user = User.builder().email("admin@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.addRole(Role.builder().name(RoleName.ADMIN).build());
        return new UserPrincipal(user);
    }

    @Test
    void aNewSessionsRefreshToken_expiresAfterTheIdleTimeout() {
        String token = tokenProvider.generateRefreshToken(principal, JwtTokenProvider.Session.start(true));

        assertThat(expiresInMs(token)).isBetween(2 * HOUR - 60_000, 2 * HOUR);
    }

    @Test
    void refreshingCanNeverPushASessionPastItsMaximum_12HoursWithoutRememberMe() {
        // Started 11 hours ago without "remember me": only one hour left, not two.
        long started = System.currentTimeMillis() / 1000 - 11 * 3600;
        String token = tokenProvider.generateRefreshToken(principal, new JwtTokenProvider.Session("s1", started, false));

        assertThat(expiresInMs(token)).isBetween(HOUR - 60_000, HOUR);
    }

    @Test
    void rememberMe_allowsSevenDays_butAdminsAreCappedAtOneDay() {
        assertThat(tokenProvider.maxSessionMs(principal, true)).isEqualTo(7 * 24 * HOUR);
        assertThat(tokenProvider.maxSessionMs(principal, false)).isEqualTo(12 * HOUR);
        assertThat(tokenProvider.maxSessionMs(admin(), true)).isEqualTo(24 * HOUR);
        assertThat(tokenProvider.maxSessionMs(admin(), false)).isEqualTo(12 * HOUR);

        // An admin session started 23.5 hours ago with "remember me" has 30 minutes left.
        long started = System.currentTimeMillis() / 1000 - (23 * 3600 + 1800);
        String token = tokenProvider.generateRefreshToken(admin(), new JwtTokenProvider.Session("s2", started, true));
        assertThat(expiresInMs(token)).isBetween(HOUR / 2 - 60_000, HOUR / 2);
    }

    @Test
    void theSessionTravelsInsideTheRefreshToken() {
        JwtTokenProvider.Session session = new JwtTokenProvider.Session("session-9", 1_790_000_000L, true);
        String token = tokenProvider.generateRefreshToken(principal, new JwtTokenProvider.Session(
                session.id(), System.currentTimeMillis() / 1000, true));

        JwtTokenProvider.Session read = tokenProvider.getSessionFromToken(token);
        assertThat(read.id()).isEqualTo("session-9");
        assertThat(read.rememberMe()).isTrue();
    }

    @Test
    void aRefreshTokenFromBeforeSessions_countsAsARememberMeSessionStartedWhenIssued() {
        String legacy = io.jsonwebtoken.Jwts.builder()
                .id("legacy-jti").subject("student@example.com").claim("userId", 42L).claim("type", "REFRESH")
                .issuedAt(new java.util.Date(1_790_000_000_000L)).expiration(new java.util.Date(System.currentTimeMillis() + HOUR))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        JwtTokenProvider.Session session = tokenProvider.getSessionFromToken(legacy);

        assertThat(session.id()).isEqualTo("legacy-jti");
        assertThat(session.startedAtEpochSeconds()).isEqualTo(1_790_000_000L);
        assertThat(session.rememberMe()).isTrue();
    }
}
