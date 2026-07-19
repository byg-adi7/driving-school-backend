package com.drivingschool.backend.gamification.repository;

import com.drivingschool.backend.gamification.entity.StudentGameStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentGameStatsRepository extends JpaRepository<StudentGameStats, Long> {

    Optional<StudentGameStats> findByStudent_Id(Long studentId);

    // GamificationMapper only dereferences student's name fields for a leaderboard row -
    // fetch-joining student+user avoids a per-row N+1 (same idiom as BookingRepository).
    @Query("SELECT s FROM StudentGameStats s JOIN FETCH s.student st JOIN FETCH st.user "
            + "WHERE st.school.id = :schoolId ORDER BY s.totalPoints DESC, s.id ASC")
    Page<StudentGameStats> findBySchoolIdOrderByTotalPointsDesc(@Param("schoolId") Long schoolId, Pageable pageable);

    long countByStudent_School_IdAndTotalPointsGreaterThan(Long schoolId, Integer totalPoints);
}
