package com.drivingschool.backend.live.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class RegisterAttendanceRequest {

    @NotNull(message = "Student ID is required")
    private final Long studentId;
}
