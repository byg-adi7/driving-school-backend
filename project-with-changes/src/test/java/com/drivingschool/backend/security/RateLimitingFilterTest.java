package com.drivingschool.backend.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    @Mock private RateLimiter rateLimiter;
    @Mock private FilterChain filterChain;

    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter(rateLimiter);
    }

    @Test
    void doFilter_optionsRequest_bypassesRateLimiterEntirely() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimiter, never()).tryConsume(any(), anyInt(), any());
    }

    @Test
    void doFilter_healthCheckPath_bypassesRateLimiterEntirely() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health/readiness");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimiter, never()).tryConsume(any(), anyInt(), any());
    }

    @Test
    void doFilter_authEndpoint_usesAuthLimitAndIpScopedKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("203.0.113.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.tryConsume(eq("ratelimit:auth:203.0.113.5"), eq(10), eq(Duration.ofMinutes(1))))
                .thenReturn(new RateLimiter.RateLimitResult(true, 9, 0));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getHeader("X-Rate-Limit-Remaining")).isEqualTo("9");
    }

    @Test
    void doFilter_nonAuthEndpoint_usesApiLimitAndIpScopedKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/schools");
        request.setRemoteAddr("203.0.113.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.tryConsume(eq("ratelimit:api:203.0.113.5"), eq(100), eq(Duration.ofMinutes(1))))
                .thenReturn(new RateLimiter.RateLimitResult(true, 99, 0));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_whenLimitExceeded_returns429AndSkipsChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("203.0.113.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.tryConsume(eq("ratelimit:auth:203.0.113.5"), eq(10), eq(Duration.ofMinutes(1))))
                .thenReturn(new RateLimiter.RateLimitResult(false, 0, 42));

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("X-Rate-Limit-Retry-After-Seconds")).isEqualTo("42");
        assertThat(response.getContentAsString()).contains("Too many requests");
    }
}
