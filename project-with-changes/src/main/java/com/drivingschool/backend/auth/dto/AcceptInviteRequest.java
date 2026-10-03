package com.drivingschool.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class AcceptInviteRequest {

    @NotBlank(message = "Token is required")
    private final String token;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private final String password;

    private final Boolean rememberMe;

    public boolean isRememberMe() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
