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
import com.drivingschool.backend.notification.dto.SendNotificationRequest;
import com.drivingschool.backend.notification.enums.NotificationChannel;
import com.drivingschool.backend.notification.service.NotificationService;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.vehicle.entity.Vehicle;
import com.drivingschool.backend.vehicle.enums.VehicleStatus;
import com.drivingschool.backend.vehicle.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class BookingServiceImpl implements BookingService {

    private static final List<BookingStatus> ACTIVE_STATUSES =
            List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);
    private static final DateTimeFormatter NOTIFICATION_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d yyyy 'at' HH:mm");

    private final BookingRepository bookingRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingMapper bookingMapper;
    private final NotificationService notificationService;

    public BookingServiceImpl(BookingRepository bookingRepository,
                              StudentProfileRepository studentProfileRepository,
                              InstructorProfileRepository instructorProfileRepository,
                              VehicleRepository vehicleRepository,
                              BookingMapper bookingMapper,
                              NotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.vehicleRepository = vehicleRepository;
        this.bookingMapper = bookingMapper;
        this.notificationService = notificationService;
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

        if (!instructor.getSchool().getId().equals(student.getSchool().getId())) {
            throw new BadRequestException("Instructor and student must belong to the same school");
        }

        LocalDateTime endAt = request.getScheduledAt().plusMinutes(request.getDurationMinutes());
        validateNoConflicts(request.getStudentId(), request.getInstructorId(), request.getVehicleId(),
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
                .build();

        Booking saved = bookingRepository.save(booking);
        log.info("Booking created: id={}, student={}, instructor={}", saved.getId(), student.getId(), instructor.getId());

        notifyStudent(booking, student, instructor);

        return bookingMapper.toResponse(saved);
    }

    private void notifyStudent(Booking booking, StudentProfile student, InstructorProfile instructor) {
        try {
            SendNotificationRequest request = SendNotificationRequest.builder()
                    .userId(student.getUser().getId())
                    .subject("Upcoming practical lesson scheduled")
                    .body("Your instructor %s %s has scheduled a %s lesson for you on %s. Please be present at the driving school at the scheduled time."
                            .formatted(instructor.getFirstName(), instructor.getLastName(),
                                    booking.getBookingType(), booking.getScheduledAt().format(NOTIFICATION_DATE_FORMAT)))
                    .channel(NotificationChannel.IN_APP)
                    .build();
            notificationService.send(request);
        } catch (Exception ex) {
            log.warn("Failed to send booking notification: bookingId={}, studentId={}", booking.getId(), student.getId(), ex);
        }
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
        Booking saved = bookingRepository.save(booking);
        notifyStudentCancelled(saved);
        return bookingMapper.toResponse(saved);
    }

    private void notifyStudentCancelled(Booking booking) {
        try {
            SendNotificationRequest request = SendNotificationRequest.builder()
                    .userId(booking.getStudent().getUser().getId())
                    .subject("Practical lesson cancelled")
                    .body("Your %s lesson scheduled for %s has been cancelled."
                            .formatted(booking.getBookingType(), booking.getScheduledAt().format(NOTIFICATION_DATE_FORMAT)))
                    .channel(NotificationChannel.IN_APP)
                    .build();
            notificationService.send(request);
        } catch (Exception ex) {
            log.warn("Failed to send cancellation notification: bookingId={}", booking.getId(), ex);
        }
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

    private void validateNoConflicts(Long studentId, Long instructorId, Long vehicleId,
                                     LocalDateTime startAt, LocalDateTime endAt, Long excludeId) {
        if (bookingRepository.existsInstructorConflict(instructorId, startAt, endAt, ACTIVE_STATUSES, excludeId)) {
            throw new BadRequestException("Instructor has a conflicting booking in this time slot");
        }
        if (bookingRepository.existsStudentConflict(studentId, startAt, endAt, ACTIVE_STATUSES, excludeId)) {
            throw new BadRequestException("Student has a conflicting booking in this time slot");
        }
        if (vehicleId != null && bookingRepository.existsVehicleConflict(vehicleId, startAt, endAt, ACTIVE_STATUSES, excludeId)) {
            throw new BadRequestException("Vehicle has a conflicting booking in this time slot");
        }
    }
}
