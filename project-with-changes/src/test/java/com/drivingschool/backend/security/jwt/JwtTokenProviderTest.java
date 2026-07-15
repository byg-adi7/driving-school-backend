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
}
