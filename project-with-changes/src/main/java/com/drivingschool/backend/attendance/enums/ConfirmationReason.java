package com.drivingschool.backend.attendance.enums;

/** Why a student's check-in couldn't be counted automatically and waits for an instructor. */
public enum ConfirmationReason {
    /** The phone was farther from the school than its check-in radius. */
    OUTSIDE_SCHOOL_AREA,
    /** The location fix was too imprecise (worse than 100 m) to tell. */
    LOCATION_NOT_PRECISE,
    /** The school hasn't set its location yet, so nothing could be measured. */
    SCHOOL_LOCATION_NOT_SET
}
