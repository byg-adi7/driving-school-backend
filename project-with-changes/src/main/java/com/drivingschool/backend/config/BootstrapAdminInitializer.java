package com.drivingschool.backend.config;

import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.role.repository.RoleRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Bootstrap Administrator Initializer
 *
 * Automatically creates the first ADMIN user on application startup.
 * This solves the bootstrap problem: without an admin, you cannot create an admin
 * (since /api/v1/auth/admin/register requires @PreAuthorize("hasRole('ADMIN')")).
 *
 * Behavior:
 * - Runs AFTER RoleDataSeeder (@Order(2) vs @Order(1))
 * - Checks if ADMIN role exists
 * - Checks if ADMIN user already exists
 * - If bootstrap enabled AND no admin exists: creates one
 * - If admin exists: does nothing (idempotent)
 * - If bootstrap disabled: does nothing
 *
 * Configuration (application.yml):
 *   app:
 *     bootstrap:
 *       admin:
 *         enabled: true
 *         email: admin@drivingschool.local
 *         password: ChangeMe123!
 *
 * Security:
 * - Password is BCrypt encoded using existing PasswordEncoder
 * - Credentials come from configuration, never hardcoded
 * - Can be disabled in production via application-prod.yml
 *
 * @author Driving School Backend
 * @version 1.0
 */
@Slf4j
@Component
@Order(2)
public class BootstrapAdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    /**
     * Constructor with dependency injection
     *
     * @param userRepository to check and save admin users
     * @param roleRepository to fetch the ADMIN role
     * @param passwordEncoder to encode the admin password securely
     * @param environment to read bootstrap configuration
     */
    public BootstrapAdminInitializer(UserRepository userRepository,
                                     RoleRepository roleRepository,
                                     PasswordEncoder passwordEncoder,
                                     Environment environment) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    /**
     * Runs on application startup to bootstrap the first ADMIN user
     *
     * Execution order:
     * 1. Application starts
     * 2. Flyway migrations run (creates schema)
     * 3. RoleDataSeeder runs (@Order(1)) - seeds roles
     * 4. THIS METHOD runs (@Order(2)) - creates first admin
     * 5. Application is ready
     *
     * @param args command line arguments (not used)
     */
    @Override
    @Transactional
    public void run(String... args) {
        try {
            // Check if bootstrap is enabled
            boolean bootstrapEnabled = environment.getProperty(
                    "app.bootstrap.admin.enabled",
                    Boolean.class,
                    true
            );

            if (!bootstrapEnabled) {
                log.info("Bootstrap admin creation is disabled in configuration");
                return;
            }

            // Check if ADMIN role exists
            Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                    .orElse(null);

            if (adminRole == null) {
                log.warn("ADMIN role does not exist, cannot create bootstrap admin");
                return;
            }

            // Check if any ADMIN user already exists
            Optional<User> existing = userRepository.findByEmail(
                    environment.getProperty("app.bootstrap.admin.email")
            );

            if (existing.isPresent()) {
                User admin = existing.get();
                // Self-heal the flag onto the pre-existing row: no SQL migration can know
                // the env-configured bootstrap email, so this is the only place that can
                // backfill it for an environment that already had a bootstrap admin before
                // this flag was introduced.
                if (!admin.isBootstrapAdmin()) {
                    admin.markAsBootstrapAdmin();
                    userRepository.save(admin);
                    log.info("Marked pre-existing bootstrap admin row as bootstrap admin: {}", admin.getEmail());
                }
                log.info("Bootstrap admin already exists ({}), skipping creation", admin.getEmail());
                return;
            }

            // Get bootstrap credentials from configuration
            String adminEmail = environment.getProperty("app.bootstrap.admin.email");
            String adminPassword = environment.getProperty("app.bootstrap.admin.password");

            // Validate configuration
            if (adminEmail == null || adminEmail.isBlank()) {
                log.error("Bootstrap admin email not configured (app.bootstrap.admin.email)");
                return;
            }

            if (adminPassword == null || adminPassword.isBlank()) {
                log.error("Bootstrap admin password not configured (app.bootstrap.admin.password)");
                return;
            }

            // Create the bootstrap admin user
            log.info("Bootstrap admin does not exist, creating with email: {}", adminEmail);

            User adminUser = User.builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .enabled(true)
                    .emailVerified(true)
                    .build();

            adminUser.addRole(adminRole);
            adminUser.markAsBootstrapAdmin();

            User savedAdminUser = userRepository.save(adminUser);

            log.info("Successfully created bootstrap admin user: {} (ID: {})",
                    savedAdminUser.getEmail(),
                    savedAdminUser.getId());
            log.info("You can now log in with these credentials:");
            log.info("  Email: {}", adminEmail);
            log.info("  Password: [configured in application.yml]");
            log.info("Please change the admin password immediately after first login!");

        } catch (Exception e) {
            log.error("Error during bootstrap admin initialization", e);
            throw new RuntimeException("Failed to initialize bootstrap admin", e);
        }
    }
}