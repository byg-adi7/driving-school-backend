package com.drivingschool.backend.security;

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
 * Rate limits every request by client IP: a tight bucket for the auth
 * endpoints (login/register/refresh/forgot-password/reset-password, the
 * classic credential-stuffing/brute-force targets) and a looser one for
 * everything else, so no endpoint is completely unthrottled. Health-check
 * paths are exempt - those are hit on a fixed interval by the platform
 * (Docker/Render) and must never 429.
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int AUTH_LIMIT = 10;
    private static final int API_LIMIT = 100;

    private final RateLimiter rateLimiter;

    public RateLimitingFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
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
        String clientIp = request.getRemoteAddr();
        String key = "ratelimit:" + (isAuthEndpoint ? "auth:" : "api:") + clientIp;
        int limit = isAuthEndpoint ? AUTH_LIMIT : API_LIMIT;

        RateLimiter.RateLimitResult result = rateLimiter.tryConsume(key, limit, WINDOW);

        if (result.allowed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(result.remaining()));
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for {} on {}", clientIp, requestURI);
            response.setStatus(429);
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(result.retryAfterSeconds()));
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Too many requests. Please try again later.\"}");
        }
    }

    private boolean isAuthEndpoint(String requestURI) {
        return requestURI.contains("/api/v1/auth/login") ||
                requestURI.contains("/api/v1/auth/register") ||
                requestURI.contains("/api/v1/auth/refresh-token") ||
                requestURI.contains("/api/v1/auth/forgot-password") ||
                requestURI.contains("/api/v1/auth/reset-password");
    }
}
