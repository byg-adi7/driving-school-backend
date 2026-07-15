package com.drivingschool.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    private final String refreshToken;
}
