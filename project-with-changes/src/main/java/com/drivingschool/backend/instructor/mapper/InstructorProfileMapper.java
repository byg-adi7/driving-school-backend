package com.drivingschool.backend.instructor.mapper;

import com.drivingschool.backend.instructor.dto.InstructorProfileResponse;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import org.springframework.stereotype.Component;

@Component
public class InstructorProfileMapper {

    public InstructorProfileResponse toResponse(InstructorProfile profile) {
        return InstructorProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUser().getId())
                .email(profile.getUser().getEmail())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .phone(profile.getPhone())
                .specialization(profile.getSpecialization())
                .licenseNumber(profile.getLicenseNumber())
                .yearsExperience(profile.getYearsExperience())
                .bio(profile.getBio())
                .profileImageUrl(profile.getUser().getProfileImageUrl())
                .accountStatus(profile.getUser().getAccountStatus().name())
                .inviteExpiresAt(profile.getUser().getInviteExpiresAt())
                .active(profile.isActive())
                .schoolId(profile.getSchool().getId())
                .schoolName(profile.getSchool().getName())
                .build();
    }
}
