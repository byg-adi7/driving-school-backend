package com.drivingschool.backend.integration;

import com.drivingschool.backend.realtime.RealtimeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Realtime push end to end: a real server on a random port, a real STOMP client over
 * a real WebSocket, and the REST API driving the events - the one thing the MockMvc
 * harness can't exercise. Unlike AbstractIntegrationTest nothing here runs in a
 * rolled-back test transaction (the server commits for real, and events are only
 * pushed after commit), so every account is created with unique emails.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.jwt.secret=integration-test-secret-at-least-32-characters-long",
                "app.bootstrap.admin.password=integration-test-admin-password"
        })
class RealtimeWebSocketIntegrationTest {

    private static final String PASSWORD = "SecurePass123!";

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        IntegrationTestContainers.register(registry);
    }

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate rest;
    @Autowired private StringRedisTemplate redisTemplate;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        // Rate-limit counters live in Redis and are shared with the other integration tests.
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(new ObjectMapper().registerModule(new JavaTimeModule()));
        stompClient.setMessageConverter(converter);
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<Map> response = rest.exchange("/api/v1" + path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).as("POST %s -> %s", path, response.getStatusCode()).isTrue();
        return (Map<String, Object>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getData(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return (Map<String, Object>) rest.exchange("/api/v1" + path, HttpMethod.GET, new HttpEntity<>(headers), Map.class)
                .getBody().get("data");
    }

    private String login(String email, String password) {
        return (String) post("/auth/login", null, Map.of("email", email, "password", password)).get("accessToken");
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() { }).get(10, TimeUnit.SECONDS);
    }

    private BlockingQueue<Map<String, Object>> subscribe(StompSession session) {
        BlockingQueue<Map<String, Object>> events = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/events", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                events.add((Map<String, Object>) payload);
            }
        });
        return events;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> next(BlockingQueue<Map<String, Object>> events, String type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> event = events.poll(500, TimeUnit.MILLISECONDS);
            if (event != null && type.equals(event.get("type"))) {
                return (Map<String, Object>) event.get("payload");
            }
        }
        throw new AssertionError("No " + type + " event within 10s");
    }

    @Test
    void messagesReadReceiptsAndAnnouncementsArrivePushedOverTheWebSocket() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String bootstrapToken = login("admin@drivingschool.local", "integration-test-admin-password");
        Map<String, Object> created = post("/schools", bootstrapToken, Map.of(
                "schoolName", "Realtime School " + suffix,
                "schoolAddress", "1 Test Street",
                "adminEmail", "rt.owner." + suffix + "@example.com",
                "adminPassword", PASSWORD));
        Object schoolId = ((Map<String, Object>) created.get("school")).get("id");

        post("/auth/register", bootstrapToken, Map.of("email", "rt.instructor." + suffix + "@example.com", "password", PASSWORD,
                "firstName", "Ina", "lastName", "Instructor", "schoolId", schoolId, "role", "INSTRUCTOR",
                "licenseNumber", "LIC-RT-" + suffix));
        post("/auth/register", bootstrapToken, Map.of("email", "rt.student." + suffix + "@example.com", "password", PASSWORD,
                "firstName", "Sam", "lastName", "Student", "schoolId", schoolId, "role", "STUDENT"));
        String instructorToken = login("rt.instructor." + suffix + "@example.com", PASSWORD);
        String studentToken = login("rt.student." + suffix + "@example.com", PASSWORD);
        Object instructorProfileId = getData("/auth/me", instructorToken).get("instructorProfileId");

        BlockingQueue<Map<String, Object>> instructorEvents = subscribe(connect(instructorToken));
        BlockingQueue<Map<String, Object>> studentEvents = subscribe(connect(studentToken));
        Thread.sleep(500); // let both SUBSCRIBE frames reach the broker before anything is published

        Map<String, Object> conversation = post("/conversations", studentToken, Map.of("participantProfileId", instructorProfileId));
        post("/conversations/" + conversation.get("id") + "/messages", studentToken, Map.of("body", "Running 5 minutes late"));

        Map<String, Object> toInstructor = next(instructorEvents, RealtimeEvent.MESSAGE_CREATED);
        assertThat(toInstructor.get("body")).isEqualTo("Running 5 minutes late");
        assertThat(toInstructor.get("mine")).isEqualTo(false);
        assertThat(next(studentEvents, RealtimeEvent.MESSAGE_CREATED).get("mine")).isEqualTo(true);

        post("/conversations/" + conversation.get("id") + "/read", instructorToken, null);
        assertThat(next(studentEvents, RealtimeEvent.CONVERSATION_READ).get("conversationId"))
                .isEqualTo(conversation.get("id"));

        post("/announcements", instructorToken, Map.of("subject", "Closure " + suffix, "body", "Route closed Monday."));
        assertThat(next(studentEvents, RealtimeEvent.ANNOUNCEMENT_CREATED).get("subject")).isEqualTo("Closure " + suffix);
    }
}
