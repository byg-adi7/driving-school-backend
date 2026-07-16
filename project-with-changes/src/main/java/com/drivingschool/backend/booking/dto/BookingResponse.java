package com.drivingschool.backend.booking.dto;

import com.drivingschool.backend.booking.enums.BookingStatus;
import com.drivingschool.backend.booking.enums.BookingType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BookingResponse {

    private final Long id;
    private final Long studentId;
    private final String studentName;
    private final Long instructorId;
    private final String instructorName;
    private final Long vehicleId;
    private final Long schoolId;
    private final LocalDateTime scheduledAt;
    private final LocalDateTime endAt;
    private final Integer durationMinutes;
    private final BookingStatus status;
    private final BookingType bookingType;
    private final String notes;
}
