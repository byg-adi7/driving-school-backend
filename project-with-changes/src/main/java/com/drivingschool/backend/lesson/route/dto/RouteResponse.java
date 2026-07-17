package com.drivingschool.backend.lesson.route.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteResponse {

    private Long id;
    private Long bookingId;
    /** InstructorProfile.id */
    private Long instructorId;
    private String instructorName;
    private String startLocation;
    private String destinationLocation;
    private Double startLatitude;
    private Double startLongitude;
    private Double destinationLatitude;
    private Double destinationLongitude;
    private Double distanceKm;
    private Long durationMinutes;
    private List<RouteCoordinateDTO> coordinates;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
