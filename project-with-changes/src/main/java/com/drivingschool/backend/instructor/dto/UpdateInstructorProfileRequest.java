package com.drivingschool.backend.instructor.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class UpdateInstructorProfileRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private final String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private final String lastName;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private final String phone;

    @Size(max = 200, message = "Specialization must not exceed 200 characters")
    private final String specialization;

    @Min(value = 0, message = "Years of experience cannot be negative")
    private final Integer yearsExperience;

    private final String bio;
}
