package com.drivingschool.backend.vehicle.repository;

import com.drivingschool.backend.vehicle.entity.Vehicle;
import com.drivingschool.backend.vehicle.enums.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findBySchoolIdAndStatus(Long schoolId, VehicleStatus status);
}
