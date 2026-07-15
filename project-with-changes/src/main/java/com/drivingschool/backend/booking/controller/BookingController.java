package com.drivingschool.backend.booking.controller;

import com.drivingschool.backend.booking.dto.BookingResponse;
import com.drivingschool.backend.booking.dto.CreateBookingRequest;
import com.drivingschool.backend.booking.service.BookingService;
import com.drivingschool.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings", description = "Lesson and session scheduling")
@SecurityRequirement(name = "Bearer Authentication")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @Operation(summary = "Create a new booking")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR') " +
            "or (hasRole('STUDENT') and @bookingSecurity.isSelfStudent(#request.studentId))")
    public ResponseEntity<ApiResponse<BookingResponse>> create(@Valid @RequestBody CreateBookingRequest request) {
        BookingResponse response = bookingService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Booking created", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get booking by ID")
    @PreAuthorize("hasRole('ADMIN') or @bookingSecurity.isParticipant(#id)")
    public ResponseEntity<ApiResponse<BookingResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getById(id)));
    }

    @PutMapping("/{id}/confirm")
    @Operation(summary = "Confirm a pending booking")
    @PreAuthorize("hasRole('ADMIN') " +
            "or (hasRole('INSTRUCTOR') and @bookingSecurity.isAssignedInstructor(#id))")
    public ResponseEntity<ApiResponse<BookingResponse>> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Booking confirmed", bookingService.confirm(id)));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel a booking")
    @PreAuthorize("hasRole('ADMIN') or @bookingSecurity.isParticipant(#id)")
    public ResponseEntity<ApiResponse<BookingResponse>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled", bookingService.cancel(id)));
    }

    @PutMapping("/{id}/complete")
    @Operation(summary = "Mark booking as completed")
    @PreAuthorize("hasRole('ADMIN') " +
            "or (hasRole('INSTRUCTOR') and @bookingSecurity.isAssignedInstructor(#id))")
    public ResponseEntity<ApiResponse<BookingResponse>> complete(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Booking completed", bookingService.complete(id)));
    }

    @GetMapping("/student/{studentId}")
    @Operation(summary = "List bookings for a student")
    @PreAuthorize("hasRole('ADMIN') " +
            "or (hasRole('STUDENT') and @bookingSecurity.isSelfStudent(#studentId)) " +
            "or (hasRole('INSTRUCTOR') and @bookingSecurity.hasTaughtStudent(#studentId))")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getByStudent(@PathVariable Long studentId) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getByStudent(studentId)));
    }

    @GetMapping("/instructor/{instructorId}")
    @Operation(summary = "List instructor schedule in date range")
    @PreAuthorize("hasRole('ADMIN') " +
            "or (hasRole('INSTRUCTOR') and @bookingSecurity.isSelfInstructor(#instructorId))")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getByInstructor(
            @PathVariable Long instructorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getByInstructor(instructorId, from, to)));
    }
}
