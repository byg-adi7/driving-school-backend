package com.drivingschool.backend.security;

import com.drivingschool.backend.common.exception.AuthenticationException;
import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserServiceTest {

    private final CurrentUserService currentUserService = new CurrentUserService();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(RoleName role, Long userId) {
        User user = User.builder().email("u@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", userId);
        user.addRole(Role.builder().name(role).build());
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void requireUserId_whenAuthenticated_returnsId() {
        authenticateAs(RoleName.STUDENT, 42L);

        assertThat(currentUserService.requireUserId()).isEqualTo(42L);
    }

    @Test
    void requireUserId_whenNotAuthenticated_throwsAuthenticationException() {
        assertThatThrownBy(currentUserService::requireUserId).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void hasRole_matchingRole_returnsTrue() {
        authenticateAs(RoleName.INSTRUCTOR, 1L);

        assertThat(currentUserService.hasRole(RoleName.INSTRUCTOR)).isTrue();
    }

    @Test
    void hasRole_differentRole_returnsFalse() {
        authenticateAs(RoleName.INSTRUCTOR, 1L);

        assertThat(currentUserService.hasRole(RoleName.ADMIN)).isFalse();
    }

    @Test
    void getRoles_stripsRolePrefix() {
        authenticateAs(RoleName.ADMIN, 1L);

        assertThat(currentUserService.getRoles()).isEqualTo(Set.of("ADMIN"));
    }
}
