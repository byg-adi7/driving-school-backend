package com.drivingschool.backend.booking.service;

import com.drivingschool.backend.booking.dto.BookingResponse;
import com.drivingschool.backend.booking.dto.CreateBookingRequest;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingService {

    BookingResponse create(CreateBookingRequest request);

    BookingResponse getById(Long id);

    BookingResponse confirm(Long id);

    BookingResponse cancel(Long id);

    BookingResponse complete(Long id);

    List<BookingResponse> getByStudent(Long studentId);

    List<BookingResponse> getByInstructor(Long instructorId, LocalDateTime from, LocalDateTime to);
}
