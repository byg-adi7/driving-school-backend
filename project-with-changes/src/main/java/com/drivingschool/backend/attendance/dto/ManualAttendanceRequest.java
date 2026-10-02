package com.drivingschool.backend.attendance.dto;

import com.drivingschool.backend.attendance.enums.DailyAttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;

@Getter
@Builder
@Jacksonized
public class ManualAttendanceRequest {

    @NotNull(message = "User ID is required")
    private final Long userId;

    @NotNull(message = "Date is required")
    private final LocalDate date;

    /** PRESENT, LATE or ABSENT. */
    @NotNull(message = "Status is required")
    private final DailyAttendanceStatus status;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private final String reason;
}
