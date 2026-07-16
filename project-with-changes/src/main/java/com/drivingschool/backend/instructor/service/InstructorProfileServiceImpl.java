package com.drivingschool.backend.instructor.service;

import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.dto.InstructorProfileResponse;
import com.drivingschool.backend.instructor.dto.UpdateInstructorActiveStatusRequest;
import com.drivingschool.backend.instructor.dto.UpdateInstructorProfileRequest;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.mapper.InstructorProfileMapper;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.security.CurrentUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class InstructorProfileServiceImpl implements InstructorProfileService {

    private final InstructorProfileRepository instructorProfileRepository;
    private final InstructorProfileMapper instructorProfileMapper;
    private final CurrentUserService currentUserService;

    public InstructorProfileServiceImpl(InstructorProfileRepository instructorProfileRepository,
                                         InstructorProfileMapper instructorProfileMapper,
                                         CurrentUserService currentUserService) {
        this.instructorProfileRepository = instructorProfileRepository;
        this.instructorProfileMapper = instructorProfileMapper;
        this.currentUserService = currentUserService;
    }

    @Override
    @Transactional(readOnly = true)
    public InstructorProfileResponse getMyProfile() {
        InstructorProfile profile = findByCurrentUser();
        return instructorProfileMapper.toResponse(profile);
    }

    @Override
    @Transactional
    public InstructorProfileResponse updateMyProfile(UpdateInstructorProfileRequest request) {
        InstructorProfile profile = findByCurrentUser();

        profile.updateProfile(request.getFirstName(), request.getLastName(), request.getPhone(),
                request.getSpecialization(), request.getYearsExperience(), request.getBio());

        InstructorProfile saved = instructorProfileRepository.save(profile);
        log.info("Updated instructor profile: {}", saved.getUser().getEmail());
        return instructorProfileMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InstructorProfileResponse> getBySchool(Long schoolId) {
        return instructorProfileRepository.findBySchoolIdExcludingDeletedUsers(schoolId).stream()
                .map(instructorProfileMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public InstructorProfileResponse updateActiveStatus(Long id, UpdateInstructorActiveStatusRequest request) {
        InstructorProfile profile = instructorProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InstructorProfile", "id", id));

        profile.setActive(request.getActive());

        InstructorProfile saved = instructorProfileRepository.save(profile);
        log.info("Updated instructor active status: {} -> {}", saved.getUser().getEmail(), request.getActive());
        return instructorProfileMapper.toResponse(saved);
    }

    private InstructorProfile findByCurrentUser() {
        Long userId = currentUserService.requireUserId();
        return instructorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("InstructorProfile", "userId", userId));
    }
}
