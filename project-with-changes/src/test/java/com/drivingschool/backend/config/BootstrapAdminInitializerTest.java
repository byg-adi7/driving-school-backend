package com.drivingschool.backend.config;

import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.role.repository.RoleRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers BootstrapAdminInitializer.run(), including a direct regression test for
 * a real startup-crash bug found while preparing this app's real deployment
 * config: app.bootstrap.admin.password is read via Environment.getProperty()
 * (not @Value/@ConfigurationProperties binding), which throws
 * IllegalArgumentException on an unresolvable placeholder rather than returning
 * null - previously crashing the whole app at startup (via the outer catch
 * re-throwing as a RuntimeException from this CommandLineRunner) whenever
 * BOOTSTRAP_ADMIN_PASSWORD was unset, instead of gracefully skipping admin
 * creation the way the initializer's own blank-check clearly intends. Fixed by
 * giving the property an empty-string YAML default; this test simulates that
 * resolved empty-string value directly, since a MockEnvironment doesn't perform
 * YAML placeholder resolution itself.
 */
@ExtendWith(MockitoExtension.class)
class BootstrapAdminInitializerTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private MockEnvironment environment;
    private BootstrapAdminInitializer initializer;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        initializer = new BootstrapAdminInitializer(userRepository, roleRepository, passwordEncoder, environment);
    }

    private Role adminRole() {
        return Role.builder().name(RoleName.ADMIN).build();
    }

    @Test
    void run_bootstrapDisabled_doesNothing() {
        environment.setProperty("app.bootstrap.admin.enabled", "false");

        initializer.run();

        verify(roleRepository, never()).findByName(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void run_adminRoleMissing_doesNotCreateUser() {
        environment.setProperty("app.bootstrap.admin.enabled", "true");
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.empty());

        initializer.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_adminAlreadyExistsAndAlreadyFlagged_skipsCreationAndSelfHeal() {
        environment.setProperty("app.bootstrap.admin.enabled", "true");
        environment.setProperty("app.bootstrap.admin.email", "admin@example.com");
        User existingAdmin = User.builder().build();
        existingAdmin.markAsBootstrapAdmin();
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole()));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(existingAdmin));

        initializer.run();

        verify(userRepository, never()).save(any());
    }

    /**
     * Backfills the bootstrap_admin flag onto a pre-existing admin row from before
     * this flag existed - no SQL migration can know the env-configured bootstrap
     * email, so this self-heal is the only place that can set it correctly.
     */
    @Test
    void run_adminAlreadyExistsButNotYetFlagged_selfHealsFlag() {
        environment.setProperty("app.bootstrap.admin.enabled", "true");
        environment.setProperty("app.bootstrap.admin.email", "admin@example.com");
        User legacyAdmin = User.builder().build();
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole()));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(legacyAdmin));

        initializer.run();

        assertThat(legacyAdmin.isBootstrapAdmin()).isTrue();
        verify(userRepository).save(legacyAdmin);
    }

    @Test
    void run_blankEmail_doesNotThrowAndSkipsCreation() {
        environment.setProperty("app.bootstrap.admin.enabled", "true");
        environment.setProperty("app.bootstrap.admin.email", "");
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole()));
        when(userRepository.findByEmail("")).thenReturn(Optional.empty());

        assertThatCode(() -> initializer.run()).doesNotThrowAnyException();

        verify(userRepository, never()).save(any());
    }

    /**
     * The actual regression test: an unset BOOTSTRAP_ADMIN_PASSWORD resolves
     * (via the now-fixed YAML default) to an empty string, not an unresolvable
     * placeholder - this must not throw, and must skip creating the admin.
     */
    @Test
    void run_blankPassword_doesNotThrowAndSkipsCreation() {
        environment.setProperty("app.bootstrap.admin.enabled", "true");
        environment.setProperty("app.bootstrap.admin.email", "admin@example.com");
        environment.setProperty("app.bootstrap.admin.password", "");
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole()));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());

        assertThatCode(() -> initializer.run()).doesNotThrowAnyException();

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_validConfig_createsEncodedAdminWithRole() {
        environment.setProperty("app.bootstrap.admin.enabled", "true");
        environment.setProperty("app.bootstrap.admin.email", "admin@example.com");
        environment.setProperty("app.bootstrap.admin.password", "ChangeMe123!");
        Role adminRole = adminRole();
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("ChangeMe123!")).thenReturn("encoded-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        initializer.run();

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                "admin@example.com".equals(user.getEmail())
                        && "encoded-hash".equals(user.getPassword())
                        && user.isEnabled()
                        && user.isBootstrapAdmin()
                        && user.getRoles().contains(adminRole)));
    }
}
