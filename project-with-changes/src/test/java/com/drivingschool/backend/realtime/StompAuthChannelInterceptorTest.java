package com.drivingschool.backend.realtime;

import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.security.CustomUserDetailsService;
import com.drivingschool.backend.security.UserPrincipal;
import com.drivingschool.backend.security.jwt.JwtTokenProvider;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private CustomUserDetailsService userDetailsService;

    private final MessageChannel channel = mock(MessageChannel.class);
    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtTokenProvider, userDetailsService);
    }

    private Message<byte[]> frame(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private StompHeaderAccessor connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        return accessor;
    }

    private UserPrincipal principal(Long id, boolean enabled) {
        User user = User.builder().email("u@example.com").password("x").enabled(enabled).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        user.addRole(Role.builder().name(RoleName.STUDENT).build());
        return new UserPrincipal(user);
    }

    private void validAccessToken(String token) {
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.isAccessToken(token)).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken(token)).thenReturn("u@example.com");
    }

    @Test
    void connect_withAValidAccessToken_setsTheUserIdAsThePrincipal() {
        validAccessToken("good");
        when(userDetailsService.loadUserByUsername("u@example.com")).thenReturn(principal(42L, true));
        StompHeaderAccessor accessor = connect("Bearer good");

        interceptor.preSend(frame(accessor), channel);

        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("42");
    }

    @Test
    void connect_withoutAToken_isRejected() {
        assertThatThrownBy(() -> interceptor.preSend(frame(connect(null)), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void connect_withAnInvalidOrRefreshToken_isRejected() {
        when(jwtTokenProvider.validateToken("refresh")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("refresh")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(frame(connect("Bearer refresh")), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void connect_forADisabledAccount_isRejected() {
        validAccessToken("good");
        when(userDetailsService.loadUserByUsername("u@example.com")).thenReturn(principal(42L, false));

        assertThatThrownBy(() -> interceptor.preSend(frame(connect("Bearer good")), channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void connect_forADeletedAccount_isRejected() {
        validAccessToken("good");
        when(userDetailsService.loadUserByUsername("u@example.com")).thenThrow(new UsernameNotFoundException("gone"));

        assertThatThrownBy(() -> interceptor.preSend(frame(connect("Bearer good")), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribe_toOwnQueue_isAllowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/events");
        accessor.setUser(StompPrincipal.forUser(42L));

        assertThatCode(() -> interceptor.preSend(frame(accessor), channel)).doesNotThrowAnyException();
    }

    @Test
    void subscribe_toAnythingElse_isRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/queue/events");
        accessor.setUser(StompPrincipal.forUser(42L));

        assertThatThrownBy(() -> interceptor.preSend(frame(accessor), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribe_withoutHavingConnected_isRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/events");

        assertThatThrownBy(() -> interceptor.preSend(frame(accessor), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void send_isAlwaysRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/user/42/queue/events");
        accessor.setUser(StompPrincipal.forUser(1L));

        assertThatThrownBy(() -> interceptor.preSend(frame(accessor), channel))
                .isInstanceOf(MessagingException.class);
    }
}
