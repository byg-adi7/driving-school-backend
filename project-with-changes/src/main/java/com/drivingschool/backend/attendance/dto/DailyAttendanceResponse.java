package com.drivingschool.backend.attendance.dto;

import com.drivingschool.backend.attendance.enums.AttendanceSource;
import com.drivingschool.backend.attendance.enums.ConfirmationReason;
import com.drivingschool.backend.attendance.enums.LessonType;
import com.drivingschool.backend.attendance.enums.DailyAttendanceStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One person's attendance for one day. In a school day list, someone with no record has
 * no id/source/checkedInAt, and status NOT_CHECKED_IN (today) or ABSENT (past days).
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DailyAttendanceResponse {

    private final Long id;
    private final Long userId;
    private final String name;
    private final String role;
    private final LocalDate date;
    private final DailyAttendanceStatus status;
    private final AttendanceSource source;
    private final LocalDateTime checkedInAt;
    private final Double distanceMeters;
    private final Double accuracyMeters;
    private final LocalDateTime confirmedAt;
    private final String confirmedByName;
    private final String recordedByName;
    // Roll call: who reviewed this entry and when (absent until staff review the day).
    private final LocalDateTime reviewedAt;
    private final String reviewedByName;
    private final String reason;
    private final LessonType lessonType;
    private final String topic;
    // Present on a check-in that needed (or needs) an instructor's confirmation.
    private final ConfirmationReason confirmationReason;
}
