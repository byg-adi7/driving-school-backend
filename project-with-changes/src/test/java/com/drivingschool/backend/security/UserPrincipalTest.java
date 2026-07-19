package com.drivingschool.backend.security;

import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPrincipalTest {

    private User userWithRole(RoleName roleName) {
        User user = User.builder().email("user@example.com").password("encoded").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", 1L);
        user.addRole(Role.builder().name(roleName).build());
        return user;
    }

    @Test
    void getAuthorities_mapsRoleToPrefixedGrantedAuthority() {
        UserPrincipal principal = new UserPrincipal(userWithRole(RoleName.INSTRUCTOR));

        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_INSTRUCTOR");
    }

    @Test
    void getAuthorities_returnsAnImmutableCollection() {
        UserPrincipal principal = new UserPrincipal(userWithRole(RoleName.STUDENT));

        assertThatThrownBy(() -> principal.getAuthorities().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void isBootstrapAdmin_reflectsUserFlag() {
        User bootstrap = userWithRole(RoleName.ADMIN);
        bootstrap.markAsBootstrapAdmin();

        assertThat(new UserPrincipal(bootstrap).isBootstrapAdmin()).isTrue();
        assertThat(new UserPrincipal(userWithRole(RoleName.ADMIN)).isBootstrapAdmin()).isFalse();
    }
}
