package com.drivingschool.backend.vehicle.dto;

import com.drivingschool.backend.vehicle.enums.VehicleStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class UpdateVehicleStatusRequest {

    @NotNull(message = "Status is required")
    private final VehicleStatus status;
}
