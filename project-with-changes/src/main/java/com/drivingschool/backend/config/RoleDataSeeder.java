package com.drivingschool.backend.config;

import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.role.repository.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Order(1)
public class RoleDataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;

    public RoleDataSeeder(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (RoleName roleName : RoleName.values()) {
            if (!roleRepository.existsByName(roleName)) {
                Role role = Role.builder()
                        .name(roleName)
                        .description(roleName.name() + " role")
                        .build();
                roleRepository.save(role);
                log.info("Seeded role: {}", roleName);
            }
        }
    }
}
