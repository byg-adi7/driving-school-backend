package com.drivingschool.backend.common.util;

import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.security.UserPrincipal;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(RoleName roleName, Long userId) {
        User user = User.builder().email("u@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", userId);
        user.addRole(Role.builder().name(roleName).build());
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void getCurrentUserRole_stripsRolePrefix() {
        authenticateAs(RoleName.INSTRUCTOR, 1L);

        // This is the exact bug this test guards against: callers throughout the
        // lesson-note/question/route modules compare this value against bare role
        // names like "INSTRUCTOR" - if this ever again returns "ROLE_INSTRUCTOR",
        // every one of those authorization checks silently denies everyone.
        assertThat(SecurityUtils.getCurrentUserRole()).isEqualTo("INSTRUCTOR");
    }

    @Test
    void getCurrentUserRole_forStudent_returnsBareStudentRole() {
        authenticateAs(RoleName.STUDENT, 2L);

        assertThat(SecurityUtils.getCurrentUserRole()).isEqualTo("STUDENT");
    }

    @Test
    void getCurrentUserRole_forAdmin_returnsBareAdminRole() {
        authenticateAs(RoleName.ADMIN, 3L);

        assertThat(SecurityUtils.getCurrentUserRole()).isEqualTo("ADMIN");
    }

    @Test
    void getCurrentUserRole_whenNotAuthenticated_returnsNull() {
        assertThat(SecurityUtils.getCurrentUserRole()).isNull();
    }

    @Test
    void getCurrentUserId_returnsAuthenticatedUsersId() {
        authenticateAs(RoleName.STUDENT, 42L);

        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(42L);
    }

    @Test
    void getCurrentUserId_whenNotAuthenticated_throwsIllegalStateException() {
        org.assertj.core.api.Assertions.assertThatThrownBy(SecurityUtils::getCurrentUserId)
                .isInstanceOf(IllegalStateException.class);
    }
}
