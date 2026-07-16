package com.drivingschool.backend.vehicle.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class CreateVehicleRequest {

    @NotBlank(message = "Registration number is required")
    @Size(max = 20, message = "Registration number must not exceed 20 characters")
    private final String registrationNumber;

    @NotBlank(message = "Make is required")
    @Size(max = 50, message = "Make must not exceed 50 characters")
    private final String make;

    @NotBlank(message = "Model is required")
    @Size(max = 50, message = "Model must not exceed 50 characters")
    private final String model;

    @NotNull(message = "Model year is required")
    @Min(value = 1980, message = "Model year must be 1980 or later")
    @Max(value = 2100, message = "Model year must not exceed 2100")
    private final Integer modelYear;

    @NotBlank(message = "Color is required")
    @Size(max = 30, message = "Color must not exceed 30 characters")
    private final String color;

    @Size(max = 100, message = "GPS device ID must not exceed 100 characters")
    private final String gpsDeviceId;

    @NotNull(message = "School ID is required")
    private final Long schoolId;
}
