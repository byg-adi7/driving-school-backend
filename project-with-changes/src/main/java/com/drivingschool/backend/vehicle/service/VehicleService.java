package com.drivingschool.backend.vehicle.service;

import com.drivingschool.backend.vehicle.dto.CreateVehicleRequest;
import com.drivingschool.backend.vehicle.dto.UpdateVehicleRequest;
import com.drivingschool.backend.vehicle.dto.UpdateVehicleStatusRequest;
import com.drivingschool.backend.vehicle.dto.VehicleResponse;

import java.util.List;

public interface VehicleService {

    VehicleResponse create(CreateVehicleRequest request);

    VehicleResponse getById(Long id);

    List<VehicleResponse> getBySchool(Long schoolId);

    VehicleResponse update(Long id, UpdateVehicleRequest request);

    VehicleResponse updateStatus(Long id, UpdateVehicleStatusRequest request);
}
