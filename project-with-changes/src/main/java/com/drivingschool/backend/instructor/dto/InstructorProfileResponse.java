package com.drivingschool.backend.instructor.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InstructorProfileResponse {

    private final Long id;
    private final Long userId;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String phone;
    private final String specialization;
    private final String licenseNumber;
    private final Integer yearsExperience;
    private final String bio;
    private final String profileImageUrl;
    private final boolean active;
    private final Long schoolId;
    private final String schoolName;
}
