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

    // BookingMapper#toResponse dereferences student, instructor, vehicle, and school
    // for every row - fetch-joining them here avoids a per-row N+1 for whichever
    // side isn't the query's own filter.
    @Query("SELECT b FROM Booking b JOIN FETCH b.student JOIN FETCH b.instructor "
            + "LEFT JOIN FETCH b.vehicle JOIN FETCH b.school "
            + "WHERE b.student.id = :studentId ORDER BY b.scheduledAt DESC")
    List<Booking> findByStudentIdOrderByScheduledAtDesc(@Param("studentId") Long studentId);

    @Query("SELECT b FROM Booking b JOIN FETCH b.student JOIN FETCH b.instructor "
            + "LEFT JOIN FETCH b.vehicle JOIN FETCH b.school "
            + "WHERE b.instructor.id = :instructorId ORDER BY b.scheduledAt ASC")
    List<Booking> findByInstructor_IdOrderByScheduledAtAsc(@Param("instructorId") Long instructorId);

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
            WHERE b.student.id = :studentId
            AND b.status IN :activeStatuses
            AND b.scheduledAt < :endAt
            AND b.endAt > :startAt
            AND (:excludeId IS NULL OR b.id <> :excludeId)
            """)
    boolean existsStudentConflict(@Param("studentId") Long studentId,
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

    @Query("SELECT b FROM Booking b JOIN FETCH b.student JOIN FETCH b.instructor "
            + "LEFT JOIN FETCH b.vehicle JOIN FETCH b.school "
            + "WHERE b.instructor.id = :instructorId AND b.scheduledAt BETWEEN :start AND :end "
            + "AND b.status IN :statuses")
    List<Booking> findByInstructor_IdAndScheduledAtBetweenAndStatusIn(
            @Param("instructorId") Long instructorId, @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end, @Param("statuses") List<BookingStatus> statuses);

    boolean existsByStudent_IdAndInstructor_Id(Long studentId, Long instructorId);
}
