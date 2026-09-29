package com.drivingschool.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class ConfirmVerificationRequest {

    @NotBlank(message = "Challenge id is required")
    private final String challengeId;

    @NotBlank(message = "Code is required")
    @Pattern(regexp = "[0-9]{6}", message = "Code must be 6 digits")
    private final String code;
}
