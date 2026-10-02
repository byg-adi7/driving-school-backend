package com.drivingschool.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private final String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private final String password;

    /** "Keep me signed in": up to 7 days instead of 12 hours (admins: 1 day either way). Optional. */
    private final Boolean rememberMe;

    public boolean isRememberMe() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
