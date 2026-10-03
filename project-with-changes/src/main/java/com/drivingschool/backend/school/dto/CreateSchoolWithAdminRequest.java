package com.drivingschool.backend.school.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class CreateSchoolWithAdminRequest {

    @NotBlank(message = "School name is required")
    @Size(max = 200, message = "School name must not exceed 200 characters")
    private final String schoolName;

    @NotBlank(message = "School address is required")
    @Size(max = 500, message = "School address must not exceed 500 characters")
    private final String schoolAddress;

    @Size(max = 20, message = "School phone must not exceed 20 characters")
    private final String schoolPhone;

    @Email(message = "School email must be valid")
    @Size(max = 255, message = "School email must not exceed 255 characters")
    private final String schoolEmail;

    /** A school cannot exist without its owning admin, so its account is created in the same request. */
    @NotBlank(message = "Admin email is required")
    @Email(message = "Admin email must be valid")
    private final String adminEmail;

    // Optional: leave it out to email the new admin an invite to choose their own.
    @Size(min = 8, max = 100, message = "Admin password must be between 8 and 100 characters")
    private final String adminPassword;
}
