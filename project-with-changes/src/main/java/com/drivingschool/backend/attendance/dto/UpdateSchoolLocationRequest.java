package com.drivingschool.backend.attendance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class UpdateSchoolLocationRequest {

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private final Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private final Double longitude;

    /** Optional; 150 m until changed. */
    @Min(value = 20, message = "Radius must be at least 20 meters")
    @Max(value = 2000, message = "Radius must be at most 2000 meters")
    private final Integer attendanceRadiusMeters;

    /** Optional IANA zone, e.g. Africa/Accra (the default). */
    @Size(max = 64)
    private final String timeZone;
}
