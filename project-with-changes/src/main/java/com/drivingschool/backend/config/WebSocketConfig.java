package com.drivingschool.backend.config;

import com.drivingschool.backend.realtime.StompAuthChannelInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Realtime push (STOMP over a plain WebSocket at /ws). Clients connect, send their
 * access token in the STOMP CONNECT frame, and subscribe to /user/queue/events -
 * see StompAuthChannelInterceptor and RealtimePublisher.
 *
 * Heartbeats every 10s both ways keep idle connections alive (Render's docs recommend
 * keepalives) and let each side notice a dead peer. Render closes every connection on
 * a deploy or restart, so clients must reconnect - documented in the frontend guide.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long HEARTBEAT_MS = 10_000;

    private final CorsProperties corsProperties;
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    public WebSocketConfig(CorsProperties corsProperties, StompAuthChannelInterceptor stompAuthChannelInterceptor) {
        this.corsProperties = corsProperties;
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Same origins as the REST API's CORS config - a browser WebSocket handshake
        // carries an Origin header, and nothing else should be able to open one.
        registry.addEndpoint("/ws").setAllowedOrigins(corsProperties.getAllowedOrigins().split(","));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[]{HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(webSocketHeartbeatScheduler());
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Bean
    public ThreadPoolTaskScheduler webSocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
