package com.drivingschool.backend.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class CreateAnnouncementRequest {

    @NotBlank(message = "Subject is required")
    @Size(max = 200, message = "Subject must not exceed 200 characters")
    private final String subject;

    @NotBlank(message = "Body is required")
    @Size(max = 5000, message = "Body must not exceed 5000 characters")
    private final String body;
}
