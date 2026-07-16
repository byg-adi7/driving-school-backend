package com.drivingschool.backend.instructor.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class UpdateInstructorActiveStatusRequest {

    @NotNull(message = "Active is required")
    private final Boolean active;
}
