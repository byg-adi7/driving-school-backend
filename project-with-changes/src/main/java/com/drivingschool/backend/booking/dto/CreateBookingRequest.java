package com.drivingschool.backend.booking.dto;

import com.drivingschool.backend.booking.enums.BookingType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

@Getter
@Builder
@Jacksonized
public class CreateBookingRequest {

    @NotNull(message = "Student ID is required")
    private final Long studentId;

    @NotNull(message = "Instructor ID is required")
    private final Long instructorId;

    private final Long vehicleId;

    @NotNull(message = "Scheduled time is required")
    @Future(message = "Scheduled time must be in the future")
    private final LocalDateTime scheduledAt;

    @NotNull(message = "Duration is required")
    @Positive(message = "Duration must be positive")
    private final Integer durationMinutes;

    @NotNull(message = "Booking type is required")
    private final BookingType bookingType;

    @Size(max = 1000)
    private final String notes;

    @Size(max = 500)
    private final String pickupLocation;
}
