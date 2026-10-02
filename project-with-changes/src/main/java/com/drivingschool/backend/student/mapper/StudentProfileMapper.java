package com.drivingschool.backend.student.mapper;

import com.drivingschool.backend.student.dto.StudentProfileResponse;
import com.drivingschool.backend.student.entity.StudentProfile;
import org.springframework.stereotype.Component;

@Component
public class StudentProfileMapper {

    public StudentProfileResponse toResponse(StudentProfile profile) {
        return StudentProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUser().getId())
                .email(profile.getUser().getEmail())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .phone(profile.getPhone())
                .dateOfBirth(profile.getDateOfBirth())
                .enrollmentDate(profile.getEnrollmentDate())
                .status(profile.getStatus())
                .profileImageUrl(profile.getUser().getProfileImageUrl())
                .schoolId(profile.getSchool().getId())
                .schoolName(profile.getSchool().getName())
                .build();
    }
}
