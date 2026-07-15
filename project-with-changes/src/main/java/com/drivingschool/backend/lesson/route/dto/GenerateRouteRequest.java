package com.drivingschool.backend.lesson.route.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GenerateRouteRequest {

    @NotNull(message = "Live session ID is required")
    private Long liveSessionId;

    @NotBlank(message = "Start location is required")
    @Size(min = 2, max = 500, message = "Start location must be between 2 and 500 characters")
    private String startLocation;

    @NotBlank(message = "Destination location is required")
    @Size(min = 2, max = 500, message = "Destination location must be between 2 and 500 characters")
    private String destinationLocation;

    @NotNull(message = "Start latitude is required")
    private Double startLatitude;

    @NotNull(message = "Start longitude is required")
    private Double startLongitude;

    @NotNull(message = "Destination latitude is required")
    private Double destinationLatitude;

    @NotNull(message = "Destination longitude is required")
    private Double destinationLongitude;
}

