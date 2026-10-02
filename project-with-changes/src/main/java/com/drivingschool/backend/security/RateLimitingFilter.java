package com.drivingschool.backend.security;

import com.drivingschool.backend.security.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Rate limits every request. A whole school's Wi-Fi shares ONE internet address, so
 * counting per address throttled a busy class together:
 * - a request carrying a valid access token is counted per USER;
 * - auth endpoints (login, refresh, password reset, verification) per address, with
 *   room for a classroom logging in at once - password guessing is limited per
 *   account in AuthServiceImpl instead;
 * - any other anonymous request per address.
 * Health-check paths are exempt - the platform hits them on a fixed interval.
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    static final int AUTH_LIMIT = 60;
    static final int ANONYMOUS_LIMIT = 100;
    static final int USER_LIMIT = 300;

    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final JwtTokenProvider jwtTokenProvider;

    public RateLimitingFilter(RateLimiter rateLimiter, ClientIpResolver clientIpResolver,
                              JwtTokenProvider jwtTokenProvider) {
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestURI = request.getRequestURI();

        if (HttpMethod.OPTIONS.matches(request.getMethod()) || requestURI.startsWith("/actuator/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean isAuthEndpoint = isAuthEndpoint(requestURI);
        String clientIp = clientIpResolver.resolve(request);
        Long userId = isAuthEndpoint ? null : authenticatedUserId(request);
        String key;
        int limit;
        if (isAuthEndpoint) {
            key = "ratelimit:auth:" + clientIp;
            limit = AUTH_LIMIT;
        } else if (userId != null) {
            key = "ratelimit:user:" + userId;
            limit = USER_LIMIT;
        } else {
            key = "ratelimit:api:" + clientIp;
            limit = ANONYMOUS_LIMIT;
        }

        RateLimiter.RateLimitResult result = rateLimiter.tryConsume(key, limit, WINDOW);

        if (result.allowed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(result.remaining()));
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for {} on {}", userId != null ? "user " + userId : clientIp, requestURI);
            response.setStatus(429);
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(result.retryAfterSeconds()));
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Too many requests. Please try again later.\"}");
        }
    }

    // The JWT filter runs after this one, so read the token here; an expired or invalid
    // one just falls back to the per-address bucket.
    private Long authenticatedUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7);
        try {
            if (jwtTokenProvider.validateToken(token) && jwtTokenProvider.isAccessToken(token)) {
                return jwtTokenProvider.getUserIdFromToken(token);
            }
        } catch (RuntimeException ignored) {
            // malformed - treat as anonymous
        }
        return null;
    }

    private boolean isAuthEndpoint(String requestURI) {
        return requestURI.contains("/api/v1/auth/login") ||
                requestURI.contains("/api/v1/auth/register") ||
                requestURI.contains("/api/v1/auth/refresh-token") ||
                requestURI.contains("/api/v1/auth/forgot-password") ||
                requestURI.contains("/api/v1/auth/reset-password") ||
                requestURI.contains("/api/v1/auth/verification/");
    }
}
