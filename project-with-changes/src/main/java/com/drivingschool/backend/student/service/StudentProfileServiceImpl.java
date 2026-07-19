package com.drivingschool.backend.student.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.role.enums.RoleName;
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
    private final InstructorProfileRepository instructorProfileRepository;

    public StudentProfileServiceImpl(StudentProfileRepository studentProfileRepository,
                                      StudentProfileMapper studentProfileMapper,
                                      CurrentUserService currentUserService,
                                      InstructorProfileRepository instructorProfileRepository) {
        this.studentProfileRepository = studentProfileRepository;
        this.studentProfileMapper = studentProfileMapper;
        this.currentUserService = currentUserService;
        this.instructorProfileRepository = instructorProfileRepository;
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
        validateCallerCanViewSchool(schoolId);

        return studentProfileRepository.findBySchoolIdExcludingDeletedUsers(schoolId).stream()
                .map(studentProfileMapper::toResponse)
                .toList();
    }

    // Same pattern as AuthServiceImpl.validateCallerCanCreate() - an instructor may
    // only view students in their own school; admin is unrestricted.
    private void validateCallerCanViewSchool(Long schoolId) {
        if (currentUserService.hasRole(RoleName.ADMIN)) {
            return;
        }

        Long callerId = currentUserService.requireUserId();
        InstructorProfile callerProfile = instructorProfileRepository.findByUserId(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor profile not found for user ID: " + callerId));

        if (!callerProfile.getSchool().getId().equals(schoolId)) {
            throw new BadRequestException("You can only view students in your own school");
        }
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
