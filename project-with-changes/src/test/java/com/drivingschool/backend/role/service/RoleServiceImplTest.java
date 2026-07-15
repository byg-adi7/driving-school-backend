package com.drivingschool.backend.role.service;

import com.drivingschool.backend.role.dto.RoleResponse;
import com.drivingschool.backend.role.entity.Role;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.role.mapper.RoleMapper;
import com.drivingschool.backend.role.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock private RoleRepository roleRepository;
    private final RoleMapper roleMapper = new RoleMapper();

    private RoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleServiceImpl(roleRepository, roleMapper);
    }

    @Test
    void findAll_mapsEveryRoleToAResponse() {
        Role admin = Role.builder().name(RoleName.ADMIN).description("Administrator").build();
        Role student = Role.builder().name(RoleName.STUDENT).description("Student").build();
        when(roleRepository.findAll()).thenReturn(List.of(admin, student));

        List<RoleResponse> result = roleService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(RoleResponse::getName).containsExactly(RoleName.ADMIN, RoleName.STUDENT);
    }

    @Test
    void findAll_noRoles_returnsEmptyList() {
        when(roleRepository.findAll()).thenReturn(List.of());

        assertThat(roleService.findAll()).isEmpty();
    }
}
