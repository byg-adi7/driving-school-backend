package com.drivingschool.backend.gamification.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StudentGameStatsTest {

    private StudentGameStats newStats() {
        return StudentGameStats.builder()
                .totalPoints(0).currentStreakWeeks(0).longestStreakWeeks(0).build();
    }

    @Test
    void applyQualifyingWeek_firstEverEvent_setsStreakToOne() {
        StudentGameStats stats = newStats();
        LocalDate monday = LocalDate.of(2026, 7, 6);

        stats.applyQualifyingWeek(monday);

        assertThat(stats.getCurrentStreakWeeks()).isEqualTo(1);
        assertThat(stats.getLongestStreakWeeks()).isEqualTo(1);
        assertThat(stats.getLastActivityWeekStart()).isEqualTo(monday);
    }

    @Test
    void applyQualifyingWeek_sameWeekTwice_doesNotIncrement() {
        StudentGameStats stats = newStats();
        LocalDate monday = LocalDate.of(2026, 7, 6);

        stats.applyQualifyingWeek(monday);
        stats.applyQualifyingWeek(monday);

        assertThat(stats.getCurrentStreakWeeks()).isEqualTo(1);
    }

    @Test
    void applyQualifyingWeek_consecutiveWeek_increments() {
        StudentGameStats stats = newStats();
        LocalDate week1 = LocalDate.of(2026, 7, 6);
        LocalDate week2 = week1.plusWeeks(1);

        stats.applyQualifyingWeek(week1);
        stats.applyQualifyingWeek(week2);

        assertThat(stats.getCurrentStreakWeeks()).isEqualTo(2);
        assertThat(stats.getLongestStreakWeeks()).isEqualTo(2);
    }

    @Test
    void applyQualifyingWeek_gapOfTwoWeeksOrMore_resetsToOne() {
        StudentGameStats stats = newStats();
        LocalDate week1 = LocalDate.of(2026, 7, 6);
        LocalDate week4 = week1.plusWeeks(3);

        stats.applyQualifyingWeek(week1);
        stats.applyQualifyingWeek(week4);

        assertThat(stats.getCurrentStreakWeeks()).isEqualTo(1);
    }

    @Test
    void applyQualifyingWeek_longestStreak_tracksRunningMaxAcrossReset() {
        StudentGameStats stats = newStats();
        LocalDate week1 = LocalDate.of(2026, 7, 6);

        stats.applyQualifyingWeek(week1);
        stats.applyQualifyingWeek(week1.plusWeeks(1));
        stats.applyQualifyingWeek(week1.plusWeeks(2));
        // gap - streak resets to 1, but longest should remain 3
        stats.applyQualifyingWeek(week1.plusWeeks(5));

        assertThat(stats.getCurrentStreakWeeks()).isEqualTo(1);
        assertThat(stats.getLongestStreakWeeks()).isEqualTo(3);
    }

    @Test
    void addPoints_accumulates() {
        StudentGameStats stats = newStats();

        stats.addPoints(10);
        stats.addPoints(20);

        assertThat(stats.getTotalPoints()).isEqualTo(30);
    }
}
