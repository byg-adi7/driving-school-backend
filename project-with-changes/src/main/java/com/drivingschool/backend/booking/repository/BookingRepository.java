package com.drivingschool.backend.booking.repository;

import com.drivingschool.backend.booking.entity.Booking;
import com.drivingschool.backend.booking.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByStudentIdOrderByScheduledAtDesc(Long studentId);

    List<Booking> findByInstructor_IdOrderByScheduledAtAsc(Long instructorId);

    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.instructor.id = :instructorId
            AND b.status IN :activeStatuses
            AND b.scheduledAt < :endAt
            AND b.endAt > :startAt
            AND (:excludeId IS NULL OR b.id <> :excludeId)
            """)
    boolean existsInstructorConflict(@Param("instructorId") Long instructorId,
                                     @Param("startAt") LocalDateTime startAt,
                                     @Param("endAt") LocalDateTime endAt,
                                     @Param("activeStatuses") List<BookingStatus> activeStatuses,
                                     @Param("excludeId") Long excludeId);

    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.vehicle.id = :vehicleId
            AND b.status IN :activeStatuses
            AND b.scheduledAt < :endAt
            AND b.endAt > :startAt
            AND (:excludeId IS NULL OR b.id <> :excludeId)
            """)
    boolean existsVehicleConflict(@Param("vehicleId") Long vehicleId,
                                  @Param("startAt") LocalDateTime startAt,
                                  @Param("endAt") LocalDateTime endAt,
                                  @Param("activeStatuses") List<BookingStatus> activeStatuses,
                                  @Param("excludeId") Long excludeId);

    List<Booking> findByInstructor_IdAndScheduledAtBetweenAndStatusIn(
            Long instructorId, LocalDateTime start, LocalDateTime end, List<BookingStatus> statuses);

    boolean existsByStudent_IdAndInstructor_Id(Long studentId, Long instructorId);
}
