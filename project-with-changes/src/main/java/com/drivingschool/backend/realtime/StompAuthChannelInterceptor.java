package com.drivingschool.backend.realtime;

import com.drivingschool.backend.security.CustomUserDetailsService;
import com.drivingschool.backend.security.UserPrincipal;
import com.drivingschool.backend.security.jwt.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Authenticates and authorizes every STOMP frame a client sends.
 *
 * Browsers can't attach an Authorization header to the WebSocket handshake, so the
 * handshake itself is public and the access token travels in the STOMP CONNECT
 * frame's "Authorization" header instead - checked exactly like the REST API's
 * JwtAuthenticationFilter: a valid, unexpired ACCESS token of an existing, enabled
 * account. After that, a client may only SUBSCRIBE to its own private queue
 * (/user/queue/...) and may never SEND - all traffic is server-to-client.
 */
@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    static final String USER_QUEUE_PREFIX = "/user/queue/";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    public StompAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider, CustomUserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> requireOwnQueue(accessor);
            case SEND -> throw new MessagingException("Sending to the server over WebSocket is not supported");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK/NACK and heartbeats need no checks.
            }
        }
        return message;
    }

    private StompPrincipal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new MessagingException("Missing bearer token");
        }
        String token = authorization.substring(BEARER_PREFIX.length());
        if (!jwtTokenProvider.validateToken(token) || !jwtTokenProvider.isAccessToken(token)) {
            throw new MessagingException("Invalid or expired access token");
        }
        try {
            UserDetails user = userDetailsService.loadUserByUsername(jwtTokenProvider.getEmailFromToken(token));
            if (!user.isEnabled()) {
                throw new MessagingException("Account is disabled");
            }
            return StompPrincipal.forUser(((UserPrincipal) user).getId());
        } catch (UsernameNotFoundException ex) {
            throw new MessagingException("Account no longer exists");
        }
    }

    private void requireOwnQueue(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (accessor.getUser() == null || destination == null || !destination.startsWith(USER_QUEUE_PREFIX)) {
            throw new MessagingException("You may only subscribe to your own queue (" + USER_QUEUE_PREFIX + "...)");
        }
    }
}
