package com.drivingschool.backend.role.mapper;

import com.drivingschool.backend.role.dto.RoleResponse;
import com.drivingschool.backend.role.entity.Role;
import org.springframework.stereotype.Component;

@Component
public class RoleMapper {

    public RoleResponse toResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .build();
    }
}
