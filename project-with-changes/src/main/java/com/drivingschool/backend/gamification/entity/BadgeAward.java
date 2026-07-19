package com.drivingschool.backend.gamification.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.gamification.enums.BadgeType;
import com.drivingschool.backend.student.entity.StudentProfile;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "badge_awards", uniqueConstraints = {
        @UniqueConstraint(name = "uk_badge_awards_student_badge", columnNames = {"student_id", "badge"})
}, indexes = {
        @Index(name = "idx_badge_awards_student_id", columnList = "student_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BadgeAward extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BadgeType badge;

    @Builder
    public BadgeAward(StudentProfile student, BadgeType badge) {
        this.student = student;
        this.badge = badge;
    }
}
