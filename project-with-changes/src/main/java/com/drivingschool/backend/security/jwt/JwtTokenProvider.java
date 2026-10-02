package com.drivingschool.backend.security.jwt;

import com.drivingschool.backend.config.JwtProperties;
import com.drivingschool.backend.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public final class JwtTokenProvider {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";
    private static final String TOKEN_TYPE_REFRESH = "REFRESH";
    private static final String CLAIM_SESSION_ID = "sid";
    private static final String CLAIM_SESSION_START = "sst";
    private static final String CLAIM_REMEMBER_ME = "rmb";

    /**
     * A login session, carried from refresh token to refresh token: when it started and
     * whether the user ticked "keep me signed in". Decides how long it may last.
     */
    public record Session(String id, long startedAtEpochSeconds, boolean rememberMe) {
        public static Session start(boolean rememberMe) {
            return new Session(UUID.randomUUID().toString(), System.currentTimeMillis() / 1000, rememberMe);
        }
    }

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UserPrincipal principal) {
        return buildToken(principal, TOKEN_TYPE_ACCESS, jwtProperties.getAccessTokenExpirationMs());
    }

    /** A refresh token for a brand-new session without "remember me". */
    public String generateRefreshToken(UserPrincipal principal) {
        return generateRefreshToken(principal, Session.start(false));
    }

    /**
     * Expires after the idle timeout, or when the session reaches its maximum length -
     * whichever is first. Each refresh issues a new one, so a session ends after that long
     * without a refresh (inactivity) and can never outlive its maximum.
     */
    public String generateRefreshToken(UserPrincipal principal, Session session) {
        long now = System.currentTimeMillis();
        long sessionEnd = session.startedAtEpochSeconds() * 1000 + maxSessionMs(principal, session.rememberMe());
        long expiry = Math.min(now + jwtProperties.getIdleTimeoutMs(), sessionEnd);
        return buildToken(principal, TOKEN_TYPE_REFRESH, new Date(now), new Date(expiry), Map.of(
                CLAIM_SESSION_ID, session.id(),
                CLAIM_SESSION_START, session.startedAtEpochSeconds(),
                CLAIM_REMEMBER_ME, session.rememberMe()));
    }

    /** Longest a session may last: 7 days with "remember me", 12 hours without; admins at most 1 day. */
    public long maxSessionMs(UserPrincipal principal, boolean rememberMe) {
        long max = rememberMe ? jwtProperties.getRefreshTokenExpirationMs() : jwtProperties.getSessionMaxMs();
        boolean admin = principal.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return admin ? Math.min(max, jwtProperties.getAdminSessionMaxMs()) : max;
    }

    /**
     * The session a refresh token belongs to. Tokens issued before sessions existed carry
     * no session claims: they count as a "remember me" session that started when issued.
     */
    public Session getSessionFromToken(String refreshToken) {
        Claims claims = parseClaims(refreshToken);
        String id = claims.get(CLAIM_SESSION_ID, String.class);
        Long start = claims.get(CLAIM_SESSION_START, Long.class);
        Boolean rememberMe = claims.get(CLAIM_REMEMBER_ME, Boolean.class);
        return new Session(id != null ? id : claims.getId(),
                start != null ? start : claims.getIssuedAt().getTime() / 1000,
                rememberMe == null || rememberMe);
    }

    private String buildToken(UserPrincipal principal, String tokenType, long expirationMs) {
        Date now = new Date();
        return buildToken(principal, tokenType, now, new Date(now.getTime() + expirationMs), Map.of());
    }

    private String buildToken(UserPrincipal principal, String tokenType, Date now, Date expiry, Map<String, Object> extraClaims) {

        List<String> roles = principal.getAuthorities().stream()
                .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                .collect(Collectors.toList());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(principal.getEmail())
                .claim(CLAIM_USER_ID, principal.getId())
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .claims(extraClaims)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public Long getUserIdFromToken(String token) {
        return parseClaims(token).get(CLAIM_USER_ID, Long.class);
    }

    public String getJtiFromToken(String token) {
        return parseClaims(token).getId();
    }

    public Date getExpirationFromToken(String token) {
        return parseClaims(token).getExpiration();
    }

    public Date getIssuedAtFromToken(String token) {
        return parseClaims(token).getIssuedAt();
    }

    public long getRefreshTokenExpirationMs() {
        return jwtProperties.getRefreshTokenExpirationMs();
    }

    public boolean isAccessToken(String token) {
        return TOKEN_TYPE_ACCESS.equals(parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isRefreshToken(String token) {
        return TOKEN_TYPE_REFRESH.equals(parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class));
    }

    // These all represent routine, client-side authentication outcomes (an
    // expired session, a tampered/garbled token, a bot probing endpoints) -
    // not server-side failures. Logging them at ERROR level was feeding
    // Sentry's logging integration one event per occurrence, so an expired
    // token - which every client hits eventually - showed up as a recurring
    // application error rather than the routine 401 it actually is.
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (SignatureException ex) {
            log.warn("Invalid JWT signature");
        } catch (MalformedJwtException ex) {
            log.warn("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            log.debug("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.warn("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.warn("JWT claims string is empty");
        }
        return false;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
