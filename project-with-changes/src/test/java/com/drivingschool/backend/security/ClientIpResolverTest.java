package com.drivingschool.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courses");
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Test
    void withTheTrustedHeaderConfigured_usesItInsteadOfTheProxyAddress() {
        MockHttpServletRequest request = request("172.71.151.230"); // a Cloudflare edge
        request.addHeader("CF-Connecting-IP", "102.176.94.206");

        assertThat(new ClientIpResolver("CF-Connecting-IP").resolve(request)).isEqualTo("102.176.94.206");
    }

    @Test
    void neverTrustsXForwardedFor_whoseFirstEntryTheClientControls() {
        MockHttpServletRequest request = request("172.71.151.230");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 102.176.94.206");
        request.addHeader("CF-Connecting-IP", "102.176.94.206");

        assertThat(new ClientIpResolver("CF-Connecting-IP").resolve(request)).isEqualTo("102.176.94.206");
    }

    @Test
    void trustedHeaderMissing_fallsBackToTheSocketAddress() {
        assertThat(new ClientIpResolver("CF-Connecting-IP").resolve(request("203.0.113.5"))).isEqualTo("203.0.113.5");
    }

    @Test
    void noHeaderConfigured_ignoresEveryForwardingHeader() {
        // Local dev / CI: nothing strips client-supplied headers, so none may be trusted.
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("CF-Connecting-IP", "7.7.7.7");

        assertThat(new ClientIpResolver("").resolve(request)).isEqualTo("127.0.0.1");
    }
}
