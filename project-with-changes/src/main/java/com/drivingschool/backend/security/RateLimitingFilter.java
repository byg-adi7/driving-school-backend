package com.drivingschool.backend.security;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting filter for authentication endpoints
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Bucket authBucket;

    public RateLimitingFilter(@Qualifier("authBucket") Bucket authBucket) {
        this.authBucket = authBucket;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestURI = request.getRequestURI();

        // Apply rate limiting to auth endpoints
        if (requestURI.contains("/api/v1/auth/login") || 
            requestURI.contains("/api/v1/auth/register") ||
            requestURI.contains("/api/v1/auth/refresh-token")) {
            
            ConsumptionProbe probe = authBucket.tryConsumeAndReturnRemaining(1);
            
            if (probe.isConsumed()) {
                response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
                filterChain.doFilter(request, response);
            } else {
                long waitForRefill = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
                log.warn("Rate limit exceeded for {}", request.getRemoteAddr());
                response.setStatus(429);
                response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
                response.getWriter().write("{\"success\":false,\"message\":\"Too many requests. Please try again later.\"}");
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
