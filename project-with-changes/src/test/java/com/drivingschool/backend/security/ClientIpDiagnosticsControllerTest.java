package com.drivingschool.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpDiagnosticsControllerTest {

    private final ClientIpDiagnosticsController controller = new ClientIpDiagnosticsController();

    @Test
    @SuppressWarnings("unchecked")
    void echoesRemoteAddressAndForwardingHeadersButNeverCredentials() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/diagnostics/client-ip");
        request.setRemoteAddr("10.0.0.7");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        request.addHeader("Authorization", "Bearer secret-token");
        request.addHeader("Cookie", "session=secret");

        Map<String, Object> body = controller.clientIp(request).getBody().getData();
        Map<String, List<String>> headers = (Map<String, List<String>>) body.get("headers");

        assertThat(body.get("remoteAddr")).isEqualTo("10.0.0.7");
        assertThat(headers.get("X-Forwarded-For")).containsExactly("203.0.113.9, 10.0.0.1");
        assertThat(headers.keySet()).noneMatch(name -> name.equalsIgnoreCase("Authorization"));
        assertThat(headers.keySet()).noneMatch(name -> name.equalsIgnoreCase("Cookie"));
    }
}
