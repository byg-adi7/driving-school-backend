package com.drivingschool.backend.attendance.dto;

import com.drivingschool.backend.attendance.enums.DailyAttendanceStatus;
import com.drivingschool.backend.role.enums.RoleName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.util.List;

/** A day's list as staff checked it: who was really there, who wasn't. Saved in one go. */
@Getter
@Builder
@Jacksonized
public class RollCallRequest {

    @NotNull(message = "Date is required")
    private final LocalDate date;

    /** STUDENT (default) or INSTRUCTOR - instructors' roll call is the school admin's. */
    private final RoleName role;

    @NotEmpty(message = "At least one entry is required")
    @Valid
    private final List<Entry> entries;

    @Getter
    @Builder
    @Jacksonized
    public static class Entry {

        @NotNull(message = "User ID is required")
        private final Long userId;

        /** PRESENT, LATE or ABSENT. */
        @NotNull(message = "Status is required")
        private final DailyAttendanceStatus status;

        /** e.g. "Signed in and left" - kept with the record. */
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        private final String reason;
    }
}
