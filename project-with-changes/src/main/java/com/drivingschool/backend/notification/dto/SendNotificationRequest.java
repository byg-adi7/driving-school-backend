package com.drivingschool.backend.notification.dto;

import com.drivingschool.backend.notification.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class SendNotificationRequest {

    @NotNull(message = "User ID is required")
    private final Long userId;

    @NotBlank(message = "Subject is required")
    @Size(max = 200, message = "Subject must not exceed 200 characters")
    private final String subject;

    @NotBlank(message = "Body is required")
    private final String body;

    @NotNull(message = "Channel is required")
    private final NotificationChannel channel;

    @Size(max = 255, message = "Recipient address must not exceed 255 characters")
    private final String recipientAddress;
}
