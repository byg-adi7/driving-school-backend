package com.drivingschool.backend.security;

import com.drivingschool.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TEMPORARY - remove once the rate limiter's client-IP rule is settled.
 *
 * RateLimitingFilter keys on request.getRemoteAddr(), which behind Render's proxy is
 * the proxy, not the visitor. Render's docs say to read X-Forwarded-For but not how many
 * hops append to it or whether a client-supplied value is stripped - which decides
 * whether that header can be trusted or merely spoofed. This echoes back exactly what
 * reaches the app for the caller's OWN request (every header except credentials, plus
 * the socket address), so it can be checked empirically - with and without a forged
 * X-Forwarded-For - instead of guessed.
 */
@RestController
@RequestMapping("/api/v1/diagnostics")
@Tag(name = "Diagnostics", description = "Temporary: client IP / proxy header echo")
public class ClientIpDiagnosticsController {

    private static final Set<String> NEVER_ECHOED = Set.of("authorization", "cookie", "proxy-authorization");

    @GetMapping("/client-ip")
    @Operation(summary = "Echo the caller's own remote address and request headers (temporary)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clientIp(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            if (!NEVER_ECHOED.contains(name.toLowerCase())) {
                headers.put(name, Collections.list(request.getHeaders(name)));
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("remoteAddr", request.getRemoteAddr());
        body.put("headers", headers);
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}
