package com.drivingschool.backend.security;

import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.user.entity.User;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Builds a real {@link UserPrincipal} for MockMvc security tests.
 *
 * {@code @WithMockUser} is enough to prove a {@code @PreAuthorize} check denies access
 * (the interceptor only inspects granted authorities), but it wires a generic Spring
 * Security principal, not this app's {@link UserPrincipal}. Every controller here reads
 * the current user via {@code SecurityUtils.getCurrentUserId()}, which requires the
 * principal to actually be a {@link UserPrincipal} - so "allowed role succeeds" tests
 * need this instead, or they 500 rather than exercising the real success path.
 */
public final class SecurityTestUtils {

    private SecurityTestUtils() {
    }

    public static RequestPostProcessor withUser(long userId, RoleName role) {
        User user = User.builder()
                .email("test-" + userId + "@example.com")
                .password("encoded-password")
                .enabled(true)
                .emailVerified(true)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        user.addRole(Role.builder().name(role).build());
        return SecurityMockMvcRequestPostProcessors.user(new UserPrincipal(user));
    }
}
