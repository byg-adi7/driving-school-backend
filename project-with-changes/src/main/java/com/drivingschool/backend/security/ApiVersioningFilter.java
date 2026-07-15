package com.drivingschool.backend.security;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * API Versioning and Request Tracking Filter
 * 
 * Responsibilities:
 * 1. Adds API version headers to all responses (X-API-Version: 1.0)
 * 2. Generates unique request IDs for distributed tracing
 * 3. Includes request ID in response headers and MDC for logging
 * 4. Enforces Content-Type validation on requests
 * 
 * Usage:
 * - Request ID available in logs: logback MDC under "requestId"
 * - All responses include: X-API-Version, X-Request-ID headers
 */
@Component
public class ApiVersioningFilter extends OncePerRequestFilter {

    private static final String API_VERSION = "1.0";
    private static final String X_API_VERSION_HEADER = "X-API-Version";
    private static final String X_REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        // Generate unique request ID
        String requestId = request.getHeader(X_REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        try {
            // Add request ID to MDC for logging correlation
            MDC.put(REQUEST_ID_MDC_KEY, requestId);

            // Add API versioning headers to response
            response.setHeader(X_API_VERSION_HEADER, API_VERSION);
            response.setHeader(X_REQUEST_ID_HEADER, requestId);

            // Validate Content-Type only when the request carries a body
            if (hasRequestBody(request)) {
                String contentType = request.getContentType();
                if (contentType == null || contentType.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Content-Type header is required\"}");
                    return;
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            // Clean up MDC to prevent memory leaks
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    /**
     * Returns true when the request method may include a body and one is present.
     */
    private boolean hasRequestBody(HttpServletRequest request) {
        String method = request.getMethod();
        if (!method.equalsIgnoreCase("POST")
                && !method.equalsIgnoreCase("PUT")
                && !method.equalsIgnoreCase("PATCH")) {
            return false;
        }
        if ("chunked".equalsIgnoreCase(request.getHeader("Transfer-Encoding"))) {
            return true;
        }
        return request.getContentLengthLong() > 0;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Skip filter for health check endpoints
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs");
    }
}
