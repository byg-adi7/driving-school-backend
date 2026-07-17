package com.drivingschool.backend.role.service;

import com.drivingschool.backend.role.dto.RoleResponse;
import com.drivingschool.backend.role.mapper.RoleMapper;
import com.drivingschool.backend.role.repository.RoleRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    public RoleServiceImpl(RoleRepository roleRepository, RoleMapper roleMapper) {
        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

    // No mutation endpoint exists for roles anywhere in the app - the table is
    // only ever changed by a migration/seed, so this cache has no eviction path
    // and relies solely on CacheConfig's 1-hour TTL for "roles".
    @Override
    @Cacheable("roles")
    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {
        return roleRepository.findAll().stream()
                .map(roleMapper::toResponse)
                .toList();
    }
}
