package com.drivingschool.backend.live.repository;

import com.drivingschool.backend.live.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findBySessionId(Long sessionId);

    Optional<Attendance> findBySessionIdAndStudentId(Long sessionId, Long studentId);

    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);
}
