package com.drivingschool.backend.attendance.repository;

import com.drivingschool.backend.attendance.entity.DailyAttendance;
import com.drivingschool.backend.role.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyAttendanceRepository extends JpaRepository<DailyAttendance, Long> {

    Optional<DailyAttendance> findByUserIdAndAttendanceDate(Long userId, LocalDate attendanceDate);

    @Query("SELECT a FROM DailyAttendance a WHERE a.user.id = :userId "
            + "AND a.attendanceDate BETWEEN :from AND :to ORDER BY a.attendanceDate DESC")
    List<DailyAttendance> findForUser(@Param("userId") Long userId,
                                      @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT a FROM DailyAttendance a WHERE a.school.id = :schoolId AND a.role = :role "
            + "AND a.attendanceDate BETWEEN :from AND :to")
    List<DailyAttendance> findForSchool(@Param("schoolId") Long schoolId, @Param("role") RoleName role,
                                        @Param("from") LocalDate from, @Param("to") LocalDate to);
}
