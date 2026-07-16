package com.drivingschool.backend.booking.mapper;

import com.drivingschool.backend.booking.dto.BookingResponse;
import com.drivingschool.backend.booking.entity.Booking;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {

    public BookingResponse toResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .studentId(booking.getStudent().getId())
                .studentName(booking.getStudent().getFirstName() + " " + booking.getStudent().getLastName())
                .instructorId(booking.getInstructor().getId())
                .instructorName(booking.getInstructor().getFirstName() + " " + booking.getInstructor().getLastName())
                .vehicleId(booking.getVehicle() != null ? booking.getVehicle().getId() : null)
                .schoolId(booking.getSchool().getId())
                .scheduledAt(booking.getScheduledAt())
                .endAt(booking.getEndAt())
                .durationMinutes(booking.getDurationMinutes())
                .status(booking.getStatus())
                .bookingType(booking.getBookingType())
                .notes(booking.getNotes())
                .build();
    }
}
