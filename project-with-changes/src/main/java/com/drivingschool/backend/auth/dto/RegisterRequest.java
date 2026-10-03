package com.drivingschool.backend.auth.dto;

import com.drivingschool.backend.role.enums.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;

@Getter
@Builder
@Jacksonized
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private final String email;

    // Optional: leave it out to email the person an invite to choose their own.
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private final String password;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private final String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private final String lastName;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private final String phone;

    private final LocalDate dateOfBirth;

    @NotNull(message = "School ID is required")
    private final Long schoolId;

    @NotNull(message = "Role is required")
    private final RoleName role;

    @Size(max = 200, message = "Specialization must not exceed 200 characters")
    private final String specialization;

    @Size(max = 50, message = "License number must not exceed 50 characters")
    private final String licenseNumber;

    private final Integer yearsExperience;
}
