package com.drivingschool.backend.attendance.enums;

/** How a daily attendance record came about. */
public enum AttendanceSource {
    /** The person checked in with their phone's location. */
    CHECK_IN,
    /** Staff entered or corrected it. */
    MANUAL
}
