package com.drivingschool.backend.booking.security;

import com.drivingschool.backend.booking.repository.BookingRepository;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resource-ownership checks for booking endpoints, referenced from
 * {@code @PreAuthorize} SpEL expressions in BookingController (e.g.
 * {@code @bookingSecurity.isParticipant(#id)}).
 *
 * Role checks alone (hasRole/hasAnyRole) only establish WHAT a caller is allowed
 * to do in general; these methods establish WHETHER a specific resource belongs
 * to them, closing the IDOR gap where any authenticated STUDENT/INSTRUCTOR could
 * act on any other user's booking purely by knowing/guessing its ID.
 *
 * Every lookup here returns true when the referenced resource doesn't exist -
 * an unknown ID is left to the service layer, which throws
 * ResourceNotFoundException (404) rather than this method returning a
 * misleading 403 for a resource that was never there.
 *
 * Class-level @Transactional is required, not optional: @PreAuthorize is
 * evaluated before the controller method runs, with open-in-view disabled, so
 * without a transaction here there is no Hibernate session left by the time
 * isParticipant()/isAssignedInstructor() walk the lazy Booking -> Student/
 * Instructor -> User chain - it throws LazyInitializationException on every
 * real HTTP request (confirmed live; masked in tests only because
 * AbstractIntegrationTest wraps each test method in its own transaction that
 * happens to span the @PreAuthorize check too).
 */
@Component("bookingSecurity")
@Transactional(readOnly = true)
public class BookingSecurity {

    private final BookingRepository bookingRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final CurrentUserService currentUserService;

    public BookingSecurity(BookingRepository bookingRepository,
                           StudentProfileRepository studentProfileRepository,
                           InstructorProfileRepository instructorProfileRepository,
                           CurrentUserService currentUserService) {
        this.bookingRepository = bookingRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.currentUserService = currentUserService;
    }

    /** Caller is either the student or the instructor on this booking. */
    public boolean isParticipant(Long bookingId) {
        Long userId = currentUserService.requireUserId();
        return bookingRepository.findById(bookingId)
                .map(b -> b.getStudent().getUser().getId().equals(userId)
                        || b.getInstructor().getUser().getId().equals(userId))
                .orElse(true);
    }

    /** Caller is the instructor assigned to this booking. */
    public boolean isAssignedInstructor(Long bookingId) {
        Long userId = currentUserService.requireUserId();
        return bookingRepository.findById(bookingId)
                .map(b -> b.getInstructor().getUser().getId().equals(userId))
                .orElse(true);
    }

    /** Caller owns the given student profile. */
    public boolean isSelfStudent(Long studentId) {
        if (studentId == null) {
            return true;
        }
        Long userId = currentUserService.requireUserId();
        return studentProfileRepository.findById(studentId)
                .map(s -> s.getUser().getId().equals(userId))
                .orElse(true);
    }

    /** Caller owns the given instructor profile. */
    public boolean isSelfInstructor(Long instructorId) {
        Long userId = currentUserService.requireUserId();
        return instructorProfileRepository.findById(instructorId)
                .map(i -> i.getUser().getId().equals(userId))
                .orElse(true);
    }

    /** Caller is an instructor who has at least one booking with the given student. */
    public boolean hasTaughtStudent(Long studentId) {
        Long userId = currentUserService.requireUserId();
        return instructorProfileRepository.findByUserId(userId)
                .map(instructor -> bookingRepository.existsByStudent_IdAndInstructor_Id(studentId, instructor.getId()))
                .orElse(false);
    }
}
