package com.drivingschool.backend.vehicle.dto;

import com.drivingschool.backend.vehicle.enums.VehicleStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class VehicleResponse {

    private final Long id;
    private final String registrationNumber;
    private final String make;
    private final String model;
    private final Integer modelYear;
    private final String color;
    private final VehicleStatus status;
    private final String gpsDeviceId;
    private final Long schoolId;
    private final String schoolName;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
