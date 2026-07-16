package com.drivingschool.backend.vehicle.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.vehicle.dto.CreateVehicleRequest;
import com.drivingschool.backend.vehicle.dto.UpdateVehicleRequest;
import com.drivingschool.backend.vehicle.dto.UpdateVehicleStatusRequest;
import com.drivingschool.backend.vehicle.dto.VehicleResponse;
import com.drivingschool.backend.vehicle.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles")
@Tag(name = "Vehicles", description = "School vehicle fleet management")
@SecurityRequirement(name = "Bearer Authentication")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    @Operation(summary = "Add a vehicle to a school's fleet")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VehicleResponse>> create(@Valid @RequestBody CreateVehicleRequest request) {
        VehicleResponse response = vehicleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Vehicle created successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get vehicle by ID")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<VehicleResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getById(id)));
    }

    @GetMapping("/school/{schoolId}")
    @Operation(summary = "List vehicles belonging to a school")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> getBySchool(@PathVariable Long schoolId) {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getBySchool(schoolId)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update vehicle details")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VehicleResponse>> update(@PathVariable Long id,
                                                                @Valid @RequestBody UpdateVehicleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Vehicle updated successfully",
                vehicleService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update vehicle status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VehicleResponse>> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateVehicleStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Vehicle status updated successfully",
                vehicleService.updateStatus(id, request)));
    }
}
