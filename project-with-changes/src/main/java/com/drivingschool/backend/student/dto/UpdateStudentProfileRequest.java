package com.drivingschool.backend.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;

@Getter
@Builder
@Jacksonized
public class UpdateStudentProfileRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private final String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private final String lastName;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private final String phone;

    @Past(message = "Date of birth must be in the past")
    private final LocalDate dateOfBirth;
}
