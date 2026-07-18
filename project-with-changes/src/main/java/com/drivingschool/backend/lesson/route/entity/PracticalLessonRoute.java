package com.drivingschool.backend.lesson.route.entity;

import com.drivingschool.backend.booking.entity.Booking;
import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "practical_lesson_routes",
        indexes = {
                @Index(name = "idx_route_instructor", columnList = "instructor_id"),
                @Index(name = "idx_route_booking", columnList = "booking_id"),
                @Index(name = "idx_route_created_at", columnList = "created_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticalLessonRoute extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

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

    // createdAt and updatedAt are inherited from BaseEntity to centralize auditing.
    // (Previously redeclared here with Hibernate's @CreationTimestamp/@UpdateTimestamp,
    // which shadowed BaseEntity's Spring Data JPA auditing fields of the same name and
    // meant created_at never actually got populated - see PRODUCTION_READINESS.md,
    // the identical bug already found and fixed in LessonNote/LessonNoteAttachment.)

    public Double getDistance() {
        return distanceMeters / 1000.0;
    }

    public Long getDurationMinutes() {
        return durationSeconds / 60;
    }
}
