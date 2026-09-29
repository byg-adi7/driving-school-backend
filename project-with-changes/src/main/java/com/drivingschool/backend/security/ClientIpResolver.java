package com.drivingschool.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The visitor's real IP, for rate limiting.
 *
 * Behind Render, request.getRemoteAddr() is a Cloudflare edge server - a different
 * one on almost every request - so it identifies nothing. Measured against the live
 * service (2026-09-29): X-Forwarded-For keeps whatever the client sent and only
 * appends the real IP, so its first entry is forgeable; CF-Connecting-IP always
 * carries the real IP, and Cloudflare rejects (HTTP 403, error 1000) any request that
 * tries to set it itself. Render routes all inbound traffic through Cloudflare, so the
 * app is never reachable without it.
 *
 * Which header to trust is configuration (app.rate-limit.client-ip-header, set to
 * CF-Connecting-IP in application-prod.yml): only a deployment that is actually behind
 * a proxy which sets it may trust it. Unset - local dev, CI - it falls back to the
 * socket address, since there a client could forge any header it likes.
 */
@Component
public class ClientIpResolver {

    private final String trustedHeader;

    public ClientIpResolver(@Value("${app.rate-limit.client-ip-header:}") String trustedHeader) {
        this.trustedHeader = trustedHeader == null ? "" : trustedHeader.trim();
    }

    public String resolve(HttpServletRequest request) {
        if (!trustedHeader.isEmpty()) {
            String value = request.getHeader(trustedHeader);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
