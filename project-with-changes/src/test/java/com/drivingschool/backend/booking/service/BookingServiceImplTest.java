package com.drivingschool.backend.booking.service;

import com.drivingschool.backend.booking.dto.BookingResponse;
import com.drivingschool.backend.booking.dto.CreateBookingRequest;
import com.drivingschool.backend.booking.entity.Booking;
import com.drivingschool.backend.booking.enums.BookingStatus;
import com.drivingschool.backend.booking.enums.BookingType;
import com.drivingschool.backend.booking.mapper.BookingMapper;
import com.drivingschool.backend.booking.repository.BookingRepository;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.vehicle.entity.Vehicle;
import com.drivingschool.backend.vehicle.enums.VehicleStatus;
import com.drivingschool.backend.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private BookingMapper bookingMapper;

    private BookingServiceImpl bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingServiceImpl(bookingRepository, studentProfileRepository,
                instructorProfileRepository, vehicleRepository, bookingMapper);
    }

    private StudentProfile studentWithId(Long id) {
        School school = School.builder().active(true).build();
        StudentProfile student = StudentProfile.builder().school(school).build();
        ReflectionTestUtils.setField(student, "id", id);
        return student;
    }

    private InstructorProfile instructorWithId(Long id, boolean active) {
        InstructorProfile instructor = InstructorProfile.builder().active(active).build();
        ReflectionTestUtils.setField(instructor, "id", id);
        return instructor;
    }

    private CreateBookingRequest.CreateBookingRequestBuilder validRequestBuilder() {
        return CreateBookingRequest.builder()
                .studentId(1L)
                .instructorId(2L)
                .scheduledAt(LocalDateTime.now().plusDays(1))
                .durationMinutes(60)
                .bookingType(BookingType.ROAD_LESSON);
    }

    // --- create ---

    @Test
    void create_withActiveInstructorAndNoConflicts_savesBookingAsPending() {
        StudentProfile student = studentWithId(1L);
        InstructorProfile instructor = instructorWithId(2L, true);
        Booking savedBooking = mock(Booking.class);
        BookingResponse expectedResponse = BookingResponse.builder().build();

        when(studentProfileRepository.findById(1L)).thenReturn(Optional.of(student));
        when(instructorProfileRepository.findById(2L)).thenReturn(Optional.of(instructor));
        when(bookingRepository.existsInstructorConflict(eq(2L), any(), any(), any(), isNull())).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);
        when(bookingMapper.toResponse(savedBooking)).thenReturn(expectedResponse);

        BookingResponse response = bookingService.create(validRequestBuilder().build());

        assertThat(response).isEqualTo(expectedResponse);
        verify(bookingRepository).save(argThat(b -> b.getStatus() == BookingStatus.PENDING));
    }

    @Test
    void create_withUnknownStudent_throwsResourceNotFoundException() {
        when(studentProfileRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.create(validRequestBuilder().build()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(instructorProfileRepository, never()).findById(any());
    }

    @Test
    void create_withUnknownInstructor_throwsResourceNotFoundException() {
        when(studentProfileRepository.findById(1L)).thenReturn(Optional.of(studentWithId(1L)));
        when(instructorProfileRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.create(validRequestBuilder().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_withInactiveInstructor_throwsBadRequestException() {
        when(studentProfileRepository.findById(1L)).thenReturn(Optional.of(studentWithId(1L)));
        when(instructorProfileRepository.findById(2L)).thenReturn(Optional.of(instructorWithId(2L, false)));

        assertThatThrownBy(() -> bookingService.create(validRequestBuilder().build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not active");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void create_withConflictingInstructorSlot_throwsBadRequestException() {
        when(studentProfileRepository.findById(1L)).thenReturn(Optional.of(studentWithId(1L)));
        when(instructorProfileRepository.findById(2L)).thenReturn(Optional.of(instructorWithId(2L, true)));
        when(bookingRepository.existsInstructorConflict(eq(2L), any(), any(), any(), isNull())).thenReturn(true);

        assertThatThrownBy(() -> bookingService.create(validRequestBuilder().build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("conflicting booking");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void create_withUnavailableVehicle_throwsBadRequestException() {
        Vehicle vehicle = Vehicle.builder().status(VehicleStatus.MAINTENANCE).build();
        ReflectionTestUtils.setField(vehicle, "id", 3L);

        when(studentProfileRepository.findById(1L)).thenReturn(Optional.of(studentWithId(1L)));
        when(instructorProfileRepository.findById(2L)).thenReturn(Optional.of(instructorWithId(2L, true)));
        when(bookingRepository.existsInstructorConflict(eq(2L), any(), any(), any(), isNull())).thenReturn(false);
        when(vehicleRepository.findById(3L)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> bookingService.create(validRequestBuilder().vehicleId(3L).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Vehicle is not available");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void create_withConflictingVehicleSlot_throwsBadRequestException() {
        // validateNoConflicts() runs (and can short-circuit) before the vehicle is even loaded,
        // so no vehicleRepository stubbing is needed/reached here.
        when(studentProfileRepository.findById(1L)).thenReturn(Optional.of(studentWithId(1L)));
        when(instructorProfileRepository.findById(2L)).thenReturn(Optional.of(instructorWithId(2L, true)));
        when(bookingRepository.existsInstructorConflict(eq(2L), any(), any(), any(), isNull())).thenReturn(false);
        when(bookingRepository.existsVehicleConflict(eq(3L), any(), any(), any(), isNull())).thenReturn(true);

        assertThatThrownBy(() -> bookingService.create(validRequestBuilder().vehicleId(3L).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Vehicle has a conflicting booking");

        verify(vehicleRepository, never()).findById(any());
    }

    // --- state transitions ---

    private Booking bookingWithStatus(BookingStatus status) {
        Booking booking = Booking.builder()
                .student(studentWithId(1L))
                .instructor(instructorWithId(2L, true))
                .school(School.builder().active(true).build())
                .scheduledAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(1).plusHours(1))
                .durationMinutes(60)
                .status(status)
                .bookingType(BookingType.ROAD_LESSON)
                .build();
        ReflectionTestUtils.setField(booking, "id", 10L);
        return booking;
    }

    @Test
    void confirm_pendingBooking_transitionsToConfirmed() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.confirm(10L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void confirm_nonPendingBooking_throwsBadRequestException() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.confirm(10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only pending bookings");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void complete_confirmedBooking_transitionsToCompleted() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.complete(10L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void complete_pendingBooking_throwsBadRequestException() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.complete(10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only confirmed bookings");
    }

    @Test
    void cancel_pendingBooking_transitionsToCancelled() {
        Booking booking = bookingWithStatus(BookingStatus.PENDING);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.cancel(10L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancel_alreadyCompletedBooking_throwsBadRequestException() {
        Booking booking = bookingWithStatus(BookingStatus.COMPLETED);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be cancelled");
    }

    @Test
    void cancel_alreadyCancelledBooking_throwsBadRequestException() {
        Booking booking = bookingWithStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be cancelled");
    }

    @Test
    void getById_unknownBooking_throwsResourceNotFoundException() {
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByStudent_mapsAllMatchingBookings() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        BookingResponse response = BookingResponse.builder().build();
        when(bookingRepository.findByStudentIdOrderByScheduledAtDesc(1L)).thenReturn(List.of(booking));
        when(bookingMapper.toResponse(booking)).thenReturn(response);

        List<BookingResponse> result = bookingService.getByStudent(1L);

        assertThat(result).containsExactly(response);
    }
}
