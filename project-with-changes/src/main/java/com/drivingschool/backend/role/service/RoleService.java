package com.drivingschool.backend.role.service;

import com.drivingschool.backend.role.dto.RoleResponse;

import java.util.List;

public interface RoleService {

    List<RoleResponse> findAll();
}
