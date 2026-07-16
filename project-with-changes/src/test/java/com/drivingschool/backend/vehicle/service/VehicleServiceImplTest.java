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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private SchoolRepository schoolRepository;
    private final VehicleMapper vehicleMapper = new VehicleMapper();

    private VehicleServiceImpl vehicleService;

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleServiceImpl(vehicleRepository, schoolRepository, vehicleMapper);
    }

    private School schoolWithId(Long id) {
        School school = School.builder().name("Test School").address("1 Main St").active(true).build();
        ReflectionTestUtils.setField(school, "id", id);
        return school;
    }

    private Vehicle vehicleWithId(Long id, School school, VehicleStatus status) {
        Vehicle vehicle = Vehicle.builder()
                .registrationNumber("ABC123")
                .make("Toyota")
                .model("Corolla")
                .modelYear(2022)
                .color("White")
                .status(status)
                .school(school)
                .build();
        ReflectionTestUtils.setField(vehicle, "id", id);
        return vehicle;
    }

    private CreateVehicleRequest.CreateVehicleRequestBuilder validCreateRequest() {
        return CreateVehicleRequest.builder()
                .registrationNumber("ABC123")
                .make("Toyota")
                .model("Corolla")
                .modelYear(2022)
                .color("White")
                .schoolId(1L);
    }

    @Test
    void create_withDuplicateRegistrationNumber_throwsBadRequestException() {
        when(vehicleRepository.existsByRegistrationNumber("ABC123")).thenReturn(true);

        assertThatThrownBy(() -> vehicleService.create(validCreateRequest().build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already in use");

        verify(schoolRepository, never()).findById(any());
    }

    @Test
    void create_whenSchoolNotFound_throwsResourceNotFoundException() {
        when(vehicleRepository.existsByRegistrationNumber("ABC123")).thenReturn(false);
        when(schoolRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.create(validCreateRequest().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_withValidRequest_savesVehicleAsAvailable() {
        School school = schoolWithId(1L);
        when(vehicleRepository.existsByRegistrationNumber("ABC123")).thenReturn(false);
        when(schoolRepository.findById(1L)).thenReturn(Optional.of(school));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> {
            Vehicle v = invocation.getArgument(0);
            ReflectionTestUtils.setField(v, "id", 10L);
            return v;
        });

        VehicleResponse response = vehicleService.create(validCreateRequest().build());

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(response.getSchoolId()).isEqualTo(1L);
    }

    @Test
    void getById_whenNotFound_throwsResourceNotFoundException() {
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_whenFound_returnsMappedResponse() {
        School school = schoolWithId(1L);
        Vehicle vehicle = vehicleWithId(5L, school, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findById(5L)).thenReturn(Optional.of(vehicle));

        VehicleResponse response = vehicleService.getById(5L);

        assertThat(response.getId()).isEqualTo(5L);
        assertThat(response.getRegistrationNumber()).isEqualTo("ABC123");
    }

    @Test
    void getBySchool_returnsAllVehiclesForSchool() {
        School school = schoolWithId(1L);
        Vehicle v1 = vehicleWithId(1L, school, VehicleStatus.AVAILABLE);
        Vehicle v2 = vehicleWithId(2L, school, VehicleStatus.MAINTENANCE);
        when(vehicleRepository.findBySchoolId(1L)).thenReturn(List.of(v1, v2));

        List<VehicleResponse> responses = vehicleService.getBySchool(1L);

        assertThat(responses).hasSize(2);
    }

    @Test
    void update_whenNotFound_throwsResourceNotFoundException() {
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());
        UpdateVehicleRequest request = UpdateVehicleRequest.builder()
                .make("Honda").model("Civic").modelYear(2023).color("Black").build();

        assertThatThrownBy(() -> vehicleService.update(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_withValidRequest_updatesDetails() {
        School school = schoolWithId(1L);
        Vehicle vehicle = vehicleWithId(5L, school, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findById(5L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(vehicle);
        UpdateVehicleRequest request = UpdateVehicleRequest.builder()
                .make("Honda").model("Civic").modelYear(2023).color("Black").build();

        VehicleResponse response = vehicleService.update(5L, request);

        assertThat(response.getMake()).isEqualTo("Honda");
        assertThat(response.getModel()).isEqualTo("Civic");
        assertThat(response.getColor()).isEqualTo("Black");
    }

    @Test
    void updateStatus_withValidRequest_updatesStatus() {
        School school = schoolWithId(1L);
        Vehicle vehicle = vehicleWithId(5L, school, VehicleStatus.AVAILABLE);
        when(vehicleRepository.findById(5L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(vehicle);
        UpdateVehicleStatusRequest request = UpdateVehicleStatusRequest.builder()
                .status(VehicleStatus.MAINTENANCE).build();

        VehicleResponse response = vehicleService.updateStatus(5L, request);

        assertThat(response.getStatus()).isEqualTo(VehicleStatus.MAINTENANCE);
    }
}
