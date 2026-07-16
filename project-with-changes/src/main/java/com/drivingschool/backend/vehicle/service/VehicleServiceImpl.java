package com.drivingschool.backend.vehicle.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.vehicle.dto.CreateVehicleRequest;
import com.drivingschool.backend.vehicle.dto.UpdateVehicleRequest;
import com.drivingschool.backend.vehicle.dto.UpdateVehicleStatusRequest;
import com.drivingschool.backend.vehicle.dto.VehicleResponse;
import com.drivingschool.backend.vehicle.entity.Vehicle;
import com.drivingschool.backend.vehicle.enums.VehicleStatus;
import com.drivingschool.backend.vehicle.mapper.VehicleMapper;
import com.drivingschool.backend.vehicle.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final SchoolRepository schoolRepository;
    private final VehicleMapper vehicleMapper;

    public VehicleServiceImpl(VehicleRepository vehicleRepository, SchoolRepository schoolRepository,
                               VehicleMapper vehicleMapper) {
        this.vehicleRepository = vehicleRepository;
        this.schoolRepository = schoolRepository;
        this.vehicleMapper = vehicleMapper;
    }

    @Override
    @Transactional
    public VehicleResponse create(CreateVehicleRequest request) {
        if (vehicleRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new BadRequestException("Registration number is already in use");
        }

        School school = schoolRepository.findById(request.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException("School", "id", request.getSchoolId()));

        Vehicle vehicle = Vehicle.builder()
                .registrationNumber(request.getRegistrationNumber())
                .make(request.getMake())
                .model(request.getModel())
                .modelYear(request.getModelYear())
                .color(request.getColor())
                .gpsDeviceId(request.getGpsDeviceId())
                .status(VehicleStatus.AVAILABLE)
                .school(school)
                .build();

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Created vehicle: {}", saved.getRegistrationNumber());
        return vehicleMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleResponse getById(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "id", id));
        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleResponse> getBySchool(Long schoolId) {
        return vehicleRepository.findBySchoolId(schoolId).stream()
                .map(vehicleMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public VehicleResponse update(Long id, UpdateVehicleRequest request) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "id", id));

        vehicle.updateDetails(request.getMake(), request.getModel(), request.getModelYear(),
                request.getColor(), request.getGpsDeviceId());

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Updated vehicle: {}", saved.getRegistrationNumber());
        return vehicleMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public VehicleResponse updateStatus(Long id, UpdateVehicleStatusRequest request) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "id", id));

        vehicle.updateStatus(request.getStatus());

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Updated vehicle status: {} -> {}", saved.getRegistrationNumber(), request.getStatus());
        return vehicleMapper.toResponse(saved);
    }
}
