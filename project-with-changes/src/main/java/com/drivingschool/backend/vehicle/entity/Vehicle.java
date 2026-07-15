package com.drivingschool.backend.vehicle.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.vehicle.enums.VehicleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vehicles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_vehicles_registration", columnNames = "registration_number")
}, indexes = {
        @Index(name = "idx_vehicles_school_id", columnList = "school_id"),
        @Index(name = "idx_vehicles_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vehicle extends BaseEntity {

    @Column(name = "registration_number", nullable = false, length = 20)
    private String registrationNumber;

    @Column(nullable = false, length = 50)
    private String make;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(name = "model_year", nullable = false)
    private Integer modelYear;

    @Column(nullable = false, length = 30)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VehicleStatus status;

    @Column(name = "gps_device_id", length = 100)
    private String gpsDeviceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @OneToMany(mappedBy = "vehicle", fetch = FetchType.LAZY)
    private List<VehicleLocation> locations = new ArrayList<>();

    @Builder
    public Vehicle(String registrationNumber, String make, String model, Integer modelYear,
                   String color, VehicleStatus status, String gpsDeviceId, School school) {
        this.registrationNumber = registrationNumber;
        this.make = make;
        this.model = model;
        this.modelYear = modelYear;
        this.color = color;
        this.status = status;
        this.gpsDeviceId = gpsDeviceId;
        this.school = school;
    }

    public void updateStatus(VehicleStatus status) {
        this.status = status;
    }
}
