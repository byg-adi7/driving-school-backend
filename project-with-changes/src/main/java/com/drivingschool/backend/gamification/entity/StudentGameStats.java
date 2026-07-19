package com.drivingschool.backend.gamification.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.student.entity.StudentProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "student_game_stats", uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_game_stats_student_id", columnNames = "student_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentGameStats extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    private StudentProfile student;

    @Column(name = "total_points", nullable = false)
    private Integer totalPoints;

    @Column(name = "current_streak_weeks", nullable = false)
    private Integer currentStreakWeeks;

    @Column(name = "longest_streak_weeks", nullable = false)
    private Integer longestStreakWeeks;

    /** Monday of the ISO week of the student's last qualifying (completed-booking) event. */
    @Column(name = "last_activity_week_start")
    private LocalDate lastActivityWeekStart;

    @Builder
    public StudentGameStats(StudentProfile student, Integer totalPoints, Integer currentStreakWeeks,
                            Integer longestStreakWeeks, LocalDate lastActivityWeekStart) {
        this.student = student;
        this.totalPoints = totalPoints;
        this.currentStreakWeeks = currentStreakWeeks;
        this.longestStreakWeeks = longestStreakWeeks;
        this.lastActivityWeekStart = lastActivityWeekStart;
    }

    public void addPoints(int points) {
        this.totalPoints += points;
    }

    /**
     * Lazily evaluated on each new qualifying (completed-booking) event - no scheduled
     * job. Holds the streak if this event is in the same week as the last one, increments
     * on a consecutive week, and resets to 1 on a gap of 2+ weeks (the completion that
     * discovers the gap is itself week 1 of a new streak, not week 0).
     */
    public void applyQualifyingWeek(LocalDate weekStart) {
        if (lastActivityWeekStart == null) {
            currentStreakWeeks = 1;
        } else if (weekStart.equals(lastActivityWeekStart)) {
            // second+ completed lesson in the same calendar week - already counted, hold
        } else if (weekStart.equals(lastActivityWeekStart.plusWeeks(1))) {
            currentStreakWeeks += 1;
        } else {
            currentStreakWeeks = 1;
        }
        lastActivityWeekStart = weekStart;
        longestStreakWeeks = Math.max(longestStreakWeeks, currentStreakWeeks);
    }
}
