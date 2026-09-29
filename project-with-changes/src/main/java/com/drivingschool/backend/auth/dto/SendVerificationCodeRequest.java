package com.drivingschool.backend.auth.dto;

import com.drivingschool.backend.auth.enums.VerificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class SendVerificationCodeRequest {

    @NotBlank(message = "Challenge id is required")
    private final String challengeId;

    @NotNull(message = "Channel is required")
    private final VerificationChannel channel;
}
