package com.drivingschool.backend.booking.entity;

import com.drivingschool.backend.booking.enums.BookingStatus;
import com.drivingschool.backend.booking.enums.BookingType;
import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.vehicle.entity.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_bookings_student_id", columnList = "student_id"),
        @Index(name = "idx_bookings_instructor_id", columnList = "instructor_id"),
        @Index(name = "idx_bookings_scheduled_at", columnList = "scheduled_at"),
        @Index(name = "idx_bookings_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instructor_id", nullable = false)
    private InstructorProfile instructor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_type", nullable = false, length = 30)
    private BookingType bookingType;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "pickup_location", length = 500)
    private String pickupLocation;

    @Builder
    public Booking(StudentProfile student, InstructorProfile instructor, Vehicle vehicle,
                   School school, LocalDateTime scheduledAt, LocalDateTime endAt,
                   Integer durationMinutes, BookingStatus status, BookingType bookingType,
                   String notes, String pickupLocation) {
        this.student = student;
        this.instructor = instructor;
        this.vehicle = vehicle;
        this.school = school;
        this.scheduledAt = scheduledAt;
        this.endAt = endAt;
        this.durationMinutes = durationMinutes;
        this.status = status;
        this.bookingType = bookingType;
        this.notes = notes;
        this.pickupLocation = pickupLocation;
    }

    public void confirm() {
        this.status = BookingStatus.CONFIRMED;
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }

    public void complete() {
        this.status = BookingStatus.COMPLETED;
    }
}
