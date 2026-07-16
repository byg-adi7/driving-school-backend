package com.drivingschool.backend.live.repository;

import com.drivingschool.backend.live.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findBySessionId(Long sessionId);

    Optional<Attendance> findBySessionIdAndStudentId(Long sessionId, Long studentId);

    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);

    /**
     * One grouped query for a whole page of sessions instead of a per-session
     * {@code findBySessionId(...).size()} call - see LiveSessionServiceImpl#getUpcomingBySchool.
     */
    @Query("SELECT a.session.id AS sessionId, COUNT(a) AS attendeeCount FROM Attendance a "
            + "WHERE a.session.id IN :sessionIds GROUP BY a.session.id")
    List<SessionAttendanceCount> countBySessionIdIn(@Param("sessionIds") List<Long> sessionIds);

    interface SessionAttendanceCount {
        Long getSessionId();
        Long getAttendeeCount();
    }
}
