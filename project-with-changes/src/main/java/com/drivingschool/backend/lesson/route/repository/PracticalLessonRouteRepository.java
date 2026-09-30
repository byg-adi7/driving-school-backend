package com.drivingschool.backend.lesson.route.repository;

import com.drivingschool.backend.lesson.route.entity.PracticalLessonRoute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PracticalLessonRouteRepository extends JpaRepository<PracticalLessonRoute, Long> {

    @Query("SELECT plr FROM PracticalLessonRoute plr WHERE plr.booking.id = :bookingId ORDER BY plr.createdAt DESC, plr.id DESC")
    List<PracticalLessonRoute> findAllByBookingIdNewestFirst(@Param("bookingId") Long bookingId);

    // A booking has one route - generating again replaces it - but rows created before
    // that rule could leave several; the newest wins instead of a NonUniqueResult 500.
    default Optional<PracticalLessonRoute> findByBookingId(Long bookingId) {
        return findAllByBookingIdNewestFirst(bookingId).stream().findFirst();
    }

    @Query("SELECT plr FROM PracticalLessonRoute plr WHERE plr.booking.student.user.id = :userId ORDER BY plr.createdAt DESC")
    Page<PracticalLessonRoute> findByStudentUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT plr FROM PracticalLessonRoute plr WHERE plr.instructor.id = :instructorId ORDER BY plr.createdAt DESC")
    Page<PracticalLessonRoute> findByInstructorId(@Param("instructorId") Long instructorId, Pageable pageable);

    @Query("SELECT plr FROM PracticalLessonRoute plr ORDER BY plr.createdAt DESC")
    Page<PracticalLessonRoute> findAllRoutes(Pageable pageable);

    @Query("SELECT plr FROM PracticalLessonRoute plr WHERE plr.instructor.school.id = :schoolId ORDER BY plr.createdAt DESC")
    Page<PracticalLessonRoute> findAllRoutesBySchoolId(@Param("schoolId") Long schoolId, Pageable pageable);
}
