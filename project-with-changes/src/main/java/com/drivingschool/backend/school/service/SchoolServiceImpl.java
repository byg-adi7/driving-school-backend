package com.drivingschool.backend.school.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.school.dto.CreateSchoolRequest;
import com.drivingschool.backend.school.dto.SchoolResponse;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.mapper.SchoolMapper;
import com.drivingschool.backend.school.repository.SchoolRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class SchoolServiceImpl implements SchoolService {

    private final SchoolRepository schoolRepository;
    private final SchoolMapper schoolMapper;

    public SchoolServiceImpl(SchoolRepository schoolRepository, SchoolMapper schoolMapper) {
        this.schoolRepository = schoolRepository;
        this.schoolMapper = schoolMapper;
    }

    // Schools have no update/deactivate endpoint today, so create() is the only
    // mutation - evicting everything on it is simplest and cheap given how rare
    // school creation is, rather than tracking individual by-id keys.
    @Override
    @Transactional
    @CacheEvict(value = "schools", allEntries = true)
    public SchoolResponse create(CreateSchoolRequest request) {
        School school = School.builder()
                .name(request.getName())
                .address(request.getAddress())
                .phone(request.getPhone())
                .email(request.getEmail())
                .active(true)
                .build();

        School saved = schoolRepository.save(school);
        log.info("Created school: {}", saved.getName());
        return schoolMapper.toResponse(saved);
    }

    @Override
    @Cacheable(value = "schools", key = "#id")
    @Transactional(readOnly = true)
    public SchoolResponse getById(Long id) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("School", "id", id));
        return schoolMapper.toResponse(school);
    }

    @Override
    @Cacheable(value = "schools", key = "'active'")
    @Transactional(readOnly = true)
    public List<SchoolResponse> getAllActive() {
        return schoolRepository.findAll().stream()
                .filter(School::isActive)
                .map(schoolMapper::toResponse)
                .toList();
    }
}
