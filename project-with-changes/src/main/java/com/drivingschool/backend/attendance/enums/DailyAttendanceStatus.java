package com.drivingschool.backend.attendance.enums;

/**
 * A person's attendance for one day. NOT_CHECKED_IN is never stored - it's what a day
 * list shows for someone with no record yet today (from tomorrow on, that's ABSENT).
 */
public enum DailyAttendanceStatus {
    PENDING_CONFIRMATION,
    PRESENT,
    LATE,
    ABSENT,
    NOT_CHECKED_IN
}
