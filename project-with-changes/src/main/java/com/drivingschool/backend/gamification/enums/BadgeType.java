package com.drivingschool.backend.gamification.enums;

/**
 * Fixed v1 badge catalog - not admin-configurable/DB-driven. One "first-time"
 * badge per points-source trigger, two streak milestones, and two points
 * thresholds give the streak/points mechanics a visible payoff without an
 * arbitrary ladder.
 */
public enum BadgeType {
    FIRST_LESSON("First Lesson", "Completed your first practical lesson"),
    QUIZ_MASTER("Quiz Master", "Passed your first quiz"),
    ROAD_READY("Road Ready", "Passed your first driving assessment"),
    FIVE_WEEK_STREAK("5-Week Streak", "Completed a lesson 5 weeks in a row"),
    TEN_WEEK_STREAK("10-Week Streak", "Completed a lesson 10 weeks in a row"),
    CENTURY_CLUB("Century Club", "Earned 100 total points"),
    HIGH_ACHIEVER("High Achiever", "Earned 500 total points");

    private final String displayName;
    private final String description;

    BadgeType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
