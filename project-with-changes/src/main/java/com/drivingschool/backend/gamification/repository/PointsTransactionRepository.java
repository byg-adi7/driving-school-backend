package com.drivingschool.backend.gamification.repository;

import com.drivingschool.backend.gamification.entity.PointsTransaction;
import com.drivingschool.backend.gamification.enums.PointsSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {

    boolean existsBySourceTypeAndSourceId(PointsSourceType sourceType, Long sourceId);

    long countByStudent_IdAndSourceType(Long studentId, PointsSourceType sourceType);
}
