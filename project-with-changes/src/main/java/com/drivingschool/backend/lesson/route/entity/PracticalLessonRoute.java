package com.drivingschool.backend.lesson.route.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "practical_lesson_routes",
        indexes = {
                @Index(name = "idx_route_instructor", columnList = "instructor_id"),
                @Index(name = "idx_route_live_session", columnList = "live_session_id"),
                @Index(name = "idx_route_created_at", columnList = "created_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticalLessonRoute extends BaseEntity {

    @Column(name = "live_session_id", nullable = false)
    private Long liveSessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id", nullable = false)
    private InstructorProfile instructor;

    @Column(name = "start_location", nullable = false, length = 500)
    private String startLocation;

    @Column(name = "destination_location", nullable = false, length = 500)
    private String destinationLocation;

    @Column(name = "start_lat", nullable = false)
    private Double startLatitude;

    @Column(name = "start_lon", nullable = false)
    private Double startLongitude;

    @Column(name = "dest_lat", nullable = false)
    private Double destinationLatitude;

    @Column(name = "dest_lon", nullable = false)
    private Double destinationLongitude;

    @Column(name = "distance_meters", nullable = false)
    private Long distanceMeters;

    @Column(name = "duration_seconds", nullable = false)
    private Long durationSeconds;

    @Lob
    @Column(name = "route_geometry", nullable = false, columnDefinition = "TEXT")
    private String routeGeometry;

    @Column(name = "external_route_id", length = 255)
    private String externalRouteId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Double getDistance() {
        return distanceMeters / 1000.0;
    }

    public Long getDurationMinutes() {
        return durationSeconds / 60;
    }
}
