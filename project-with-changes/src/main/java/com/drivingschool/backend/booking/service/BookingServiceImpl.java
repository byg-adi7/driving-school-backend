package com.drivingschool.backend.booking.service;

import com.drivingschool.backend.booking.dto.BookingResponse;
import com.drivingschool.backend.booking.dto.CreateBookingRequest;
import com.drivingschool.backend.booking.entity.Booking;
import com.drivingschool.backend.booking.enums.BookingStatus;
import com.drivingschool.backend.booking.mapper.BookingMapper;
import com.drivingschool.backend.booking.repository.BookingRepository;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.vehicle.entity.Vehicle;
import com.drivingschool.backend.vehicle.enums.VehicleStatus;
import com.drivingschool.backend.vehicle.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class BookingServiceImpl implements BookingService {

    private static final List<BookingStatus> ACTIVE_STATUSES =
            List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

    private final BookingRepository bookingRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingMapper bookingMapper;

    public BookingServiceImpl(BookingRepository bookingRepository,
                              StudentProfileRepository studentProfileRepository,
                              InstructorProfileRepository instructorProfileRepository,
                              VehicleRepository vehicleRepository,
                              BookingMapper bookingMapper) {
        this.bookingRepository = bookingRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.vehicleRepository = vehicleRepository;
        this.bookingMapper = bookingMapper;
    }

    @Override
    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        StudentProfile student = studentProfileRepository.findById(request.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("StudentProfile", "id", request.getStudentId()));

        InstructorProfile instructor = instructorProfileRepository.findById(request.getInstructorId())
                .orElseThrow(() -> new ResourceNotFoundException("InstructorProfile", "id", request.getInstructorId()));

        if (!instructor.isActive()) {
            throw new BadRequestException("Instructor is not active");
        }

        LocalDateTime endAt = request.getScheduledAt().plusMinutes(request.getDurationMinutes());
        validateNoConflicts(request.getInstructorId(), request.getVehicleId(),
                request.getScheduledAt(), endAt, null);

        Vehicle vehicle = null;
        if (request.getVehicleId() != null) {
            vehicle = vehicleRepository.findById(request.getVehicleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "id", request.getVehicleId()));
            if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
                throw new BadRequestException("Vehicle is not available");
            }
        }

        Booking booking = Booking.builder()
                .student(student)
                .instructor(instructor)
                .vehicle(vehicle)
                .school(student.getSchool())
                .scheduledAt(request.getScheduledAt())
                .endAt(endAt)
                .durationMinutes(request.getDurationMinutes())
                .status(BookingStatus.PENDING)
                .bookingType(request.getBookingType())
                .notes(request.getNotes())
                .pickupLocation(request.getPickupLocation())
                .build();

        Booking saved = bookingRepository.save(booking);
        log.info("Booking created: id={}, student={}, instructor={}", saved.getId(), student.getId(), instructor.getId());
        return bookingMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getById(Long id) {
        return bookingMapper.toResponse(findBooking(id));
    }

    @Override
    @Transactional
    public BookingResponse confirm(Long id) {
        Booking booking = findBooking(id);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BadRequestException("Only pending bookings can be confirmed");
        }
        booking.confirm();
        return bookingMapper.toResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponse cancel(Long id) {
        Booking booking = findBooking(id);
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Booking cannot be cancelled");
        }
        booking.cancel();
        return bookingMapper.toResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponse complete(Long id) {
        Booking booking = findBooking(id);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BadRequestException("Only confirmed bookings can be completed");
        }
        booking.complete();
        return bookingMapper.toResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getByStudent(Long studentId) {
        return bookingRepository.findByStudentIdOrderByScheduledAtDesc(studentId).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getByInstructor(Long instructorId, LocalDateTime from, LocalDateTime to) {
        return bookingRepository
                .findByInstructor_IdAndScheduledAtBetweenAndStatusIn(instructorId, from, to, ACTIVE_STATUSES)
                .stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    private Booking findBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", id));
    }

    private void validateNoConflicts(Long instructorId, Long vehicleId,
                                     LocalDateTime startAt, LocalDateTime endAt, Long excludeId) {
        if (bookingRepository.existsInstructorConflict(instructorId, startAt, endAt, ACTIVE_STATUSES, excludeId)) {
            throw new BadRequestException("Instructor has a conflicting booking in this time slot");
        }
        if (vehicleId != null && bookingRepository.existsVehicleConflict(vehicleId, startAt, endAt, ACTIVE_STATUSES, excludeId)) {
            throw new BadRequestException("Vehicle has a conflicting booking in this time slot");
        }
    }
}
