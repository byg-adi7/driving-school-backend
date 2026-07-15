package com.drivingschool.backend.lesson.question.enums;

public enum QuestionStatus {
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    ANSWERED("Answered"),
    CLOSED("Closed");

    private final String displayName;

    QuestionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
