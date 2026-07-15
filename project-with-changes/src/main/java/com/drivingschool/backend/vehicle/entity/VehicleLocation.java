package com.drivingschool.backend.vehicle.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicle_locations", indexes = {
        @Index(name = "idx_vehicle_locations_vehicle_id", columnList = "vehicle_id"),
        @Index(name = "idx_vehicle_locations_recorded_at", columnList = "recorded_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleLocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(precision = 6, scale = 2)
    private BigDecimal speed;

    @Column(precision = 5, scale = 2)
    private BigDecimal heading;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Builder
    public VehicleLocation(Vehicle vehicle, BigDecimal latitude, BigDecimal longitude,
                           BigDecimal speed, BigDecimal heading, LocalDateTime recordedAt) {
        this.vehicle = vehicle;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.heading = heading;
        this.recordedAt = recordedAt;
    }
}
