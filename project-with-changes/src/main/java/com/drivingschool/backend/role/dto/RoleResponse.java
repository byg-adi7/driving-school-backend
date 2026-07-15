package com.drivingschool.backend.role.dto;

import com.drivingschool.backend.role.enums.RoleName;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoleResponse {

    private final Long id;
    private final RoleName name;
    private final String description;
}
