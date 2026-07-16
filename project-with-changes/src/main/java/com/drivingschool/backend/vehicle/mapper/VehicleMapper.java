package com.drivingschool.backend.vehicle.mapper;

import com.drivingschool.backend.vehicle.dto.VehicleResponse;
import com.drivingschool.backend.vehicle.entity.Vehicle;
import org.springframework.stereotype.Component;

@Component
public class VehicleMapper {

    public VehicleResponse toResponse(Vehicle vehicle) {
        return VehicleResponse.builder()
                .id(vehicle.getId())
                .registrationNumber(vehicle.getRegistrationNumber())
                .make(vehicle.getMake())
                .model(vehicle.getModel())
                .modelYear(vehicle.getModelYear())
                .color(vehicle.getColor())
                .status(vehicle.getStatus())
                .gpsDeviceId(vehicle.getGpsDeviceId())
                .schoolId(vehicle.getSchool().getId())
                .schoolName(vehicle.getSchool().getName())
                .createdAt(vehicle.getCreatedAt())
                .updatedAt(vehicle.getUpdatedAt())
                .build();
    }
}
