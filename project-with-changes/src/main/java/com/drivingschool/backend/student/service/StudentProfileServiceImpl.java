package com.drivingschool.backend.student.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.student.dto.StudentProfileResponse;
import com.drivingschool.backend.student.dto.UpdateStudentProfileRequest;
import com.drivingschool.backend.student.dto.UpdateStudentStatusRequest;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.mapper.StudentProfileMapper;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class StudentProfileServiceImpl implements StudentProfileService {

    private final StudentProfileRepository studentProfileRepository;
    private final StudentProfileMapper studentProfileMapper;
    private final CurrentUserService currentUserService;

    public StudentProfileServiceImpl(StudentProfileRepository studentProfileRepository,
                                      StudentProfileMapper studentProfileMapper,
                                      CurrentUserService currentUserService) {
        this.studentProfileRepository = studentProfileRepository;
        this.studentProfileMapper = studentProfileMapper;
        this.currentUserService = currentUserService;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentProfileResponse getMyProfile() {
        StudentProfile profile = findByCurrentUser();
        return studentProfileMapper.toResponse(profile);
    }

    @Override
    @Transactional
    public StudentProfileResponse updateMyProfile(UpdateStudentProfileRequest request) {
        StudentProfile profile = findByCurrentUser();

        profile.updateProfile(request.getFirstName(), request.getLastName(), request.getPhone(),
                request.getDateOfBirth(), request.getProfileImageUrl());

        StudentProfile saved = studentProfileRepository.save(profile);
        log.info("Updated student profile: {}", saved.getUser().getEmail());
        return studentProfileMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentProfileResponse> getBySchool(Long schoolId) {
        return studentProfileRepository.findBySchoolIdExcludingDeletedUsers(schoolId).stream()
                .map(studentProfileMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public StudentProfileResponse updateStatus(Long id, UpdateStudentStatusRequest request) {
        StudentProfile profile = studentProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StudentProfile", "id", id));

        profile.updateStatus(request.getStatus());

        StudentProfile saved = studentProfileRepository.save(profile);
        log.info("Updated student status: {} -> {}", saved.getUser().getEmail(), request.getStatus());
        return studentProfileMapper.toResponse(saved);
    }

    private StudentProfile findByCurrentUser() {
        Long userId = currentUserService.requireUserId();
        return studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentProfile", "userId", userId));
    }
}
