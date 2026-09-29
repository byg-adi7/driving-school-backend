package com.drivingschool.backend.security.jwt;

import com.drivingschool.backend.security.CustomUserDetailsService;
import com.drivingschool.backend.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private FilterChain filterChain;
    @Mock private UserPrincipal userPrincipal;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_noToken_proceedsUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/schools");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_invalidToken_proceedsUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/schools");
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_validTokenForExistingUser_setsAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/schools");
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenProvider.validateToken("good-token")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("good-token")).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken("good-token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userPrincipal);
        when(userPrincipal.isEnabled()).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userPrincipal);
        verify(filterChain).doFilter(request, response);
    }

    /**
     * A well-formed, signed, unexpired token can still name a user that no longer
     * exists - e.g. a bootstrap-approved school/admin cascade-delete hard-deletes
     * the row entirely (unlike every other account-removal path, which only
     * soft-deletes). This must not crash the filter chain: ExceptionTranslationFilter
     * (later in the chain) can only catch exceptions thrown by filters invoked after
     * it, not by this one, so an uncaught exception here would propagate raw instead
     * of correctly falling through to "unauthenticated".
     */
    @Test
    void doFilter_validTokenForDeletedUser_proceedsUnauthenticatedWithoutThrowing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/schools");
        request.addHeader("Authorization", "Bearer stale-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenProvider.validateToken("stale-token")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("stale-token")).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken("stale-token")).thenReturn("deleted@example.com");
        when(userDetailsService.loadUserByUsername("deleted@example.com"))
                .thenThrow(new UsernameNotFoundException("User not found with email: deleted@example.com"));

        assertThatCode(() -> filter.doFilter(request, response, filterChain)).doesNotThrowAnyException();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    /**
     * An access token issued before the account was disabled or soft-deleted must stop
     * authenticating immediately, not keep working until it expires.
     */
    @Test
    void doFilter_validTokenForDisabledUser_proceedsUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/schools");
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenProvider.validateToken("good-token")).thenReturn(true);
        when(jwtTokenProvider.isAccessToken("good-token")).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken("good-token")).thenReturn("disabled@example.com");
        when(userDetailsService.loadUserByUsername("disabled@example.com")).thenReturn(userPrincipal);
        when(userPrincipal.isEnabled()).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
