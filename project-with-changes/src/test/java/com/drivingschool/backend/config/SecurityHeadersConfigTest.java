package com.drivingschool.backend.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersConfigTest {

    private final SecurityHeadersConfig config = new SecurityHeadersConfig();
    private Logger filterLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        filterLogger = (Logger) LoggerFactory.getLogger(CommonsRequestLoggingFilter.class);
        filterLogger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(appender);
    }

    @Test
    void requestLoggingFilter_redactsSensitiveHeaders_butKeepsOthers() throws Exception {
        CommonsRequestLoggingFilter filter = config.requestLoggingFilter();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/vehicles");
        request.addHeader("Authorization", "Bearer super-secret-token");
        request.addHeader("Cookie", "session=super-secret-session");
        request.addHeader("X-Request-Id", "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        String logged = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", String::concat);

        assertThat(logged).doesNotContain("super-secret-token");
        assertThat(logged).doesNotContain("super-secret-session");
        assertThat(logged).contains("trace-123");
    }
}
