package com.drivingschool.backend.gamification.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.gamification.enums.PointsSourceType;
import com.drivingschool.backend.student.entity.StudentProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Append-only points ledger. The unique constraint on (source_type, source_id) is the
 * actual enforcement of "award once per triggering event" - for QUIZ_PASSED, source_id
 * is the quiz's id (not the submission's), so a second passing attempt at the same quiz
 * can never insert a second row here.
 */
@Entity
@Table(name = "points_transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_points_transactions_source", columnNames = {"source_type", "source_id"})
}, indexes = {
        @Index(name = "idx_points_transactions_student_id", columnList = "student_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointsTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(nullable = false)
    private Integer points;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private PointsSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(length = 255)
    private String description;

    @Builder
    public PointsTransaction(StudentProfile student, Integer points, PointsSourceType sourceType,
                             Long sourceId, String description) {
        this.student = student;
        this.points = points;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.description = description;
    }
}
