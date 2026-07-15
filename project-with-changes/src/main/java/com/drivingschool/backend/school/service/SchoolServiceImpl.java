package com.drivingschool.backend.school.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.school.dto.CreateSchoolRequest;
import com.drivingschool.backend.school.dto.SchoolResponse;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.mapper.SchoolMapper;
import com.drivingschool.backend.school.repository.SchoolRepository;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    @Transactional
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
    @Transactional(readOnly = true)
    public SchoolResponse getById(Long id) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("School", "id", id));
        return schoolMapper.toResponse(school);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SchoolResponse> getAllActive() {
        return schoolRepository.findAll().stream()
                .filter(School::isActive)
                .map(schoolMapper::toResponse)
                .toList();
    }
}
