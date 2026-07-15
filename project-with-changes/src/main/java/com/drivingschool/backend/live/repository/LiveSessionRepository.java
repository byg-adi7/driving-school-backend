package com.drivingschool.backend.live.repository;

import com.drivingschool.backend.live.entity.LiveSession;
import com.drivingschool.backend.live.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {

    List<LiveSession> findByInstructor_IdAndStatus(Long instructorId, SessionStatus status);

    List<LiveSession> findBySchoolIdAndScheduledAtBetween(Long schoolId, LocalDateTime start, LocalDateTime end);
}
