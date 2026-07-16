package com.drivingschool.backend.live.repository;

import com.drivingschool.backend.live.entity.LiveSession;
import com.drivingschool.backend.live.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {

    List<LiveSession> findByInstructor_IdAndStatus(Long instructorId, SessionStatus status);

    @Query("SELECT ls FROM LiveSession ls JOIN FETCH ls.instructor "
            + "WHERE ls.school.id = :schoolId AND ls.scheduledAt BETWEEN :start AND :end")
    List<LiveSession> findBySchoolIdAndScheduledAtBetween(@Param("schoolId") Long schoolId,
                                                           @Param("start") LocalDateTime start,
                                                           @Param("end") LocalDateTime end);
}
