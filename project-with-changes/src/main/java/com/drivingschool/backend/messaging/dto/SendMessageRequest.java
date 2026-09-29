package com.drivingschool.backend.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class SendMessageRequest {

    @NotBlank(message = "Message body is required")
    @Size(max = 5000, message = "Message must not exceed 5000 characters")
    private final String body;
}
