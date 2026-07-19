package com.drivingschool.backend.gamification.repository;

import com.drivingschool.backend.gamification.entity.BadgeAward;
import com.drivingschool.backend.gamification.enums.BadgeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BadgeAwardRepository extends JpaRepository<BadgeAward, Long> {

    boolean existsByStudent_IdAndBadge(Long studentId, BadgeType badge);

    List<BadgeAward> findByStudent_IdOrderByCreatedAtAsc(Long studentId);
}
