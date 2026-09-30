package com.drivingschool.backend.live.dto;

import com.drivingschool.backend.live.enums.AttendanceStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttendanceResponse {

    private final Long id;
    private final Long sessionId;
    private final Long studentId;
    private final String studentName;
    private final AttendanceStatus status;
    private final LocalDateTime checkedInAt;
    // Only in the register response: the link the student just unlocked.
    private final String meetingUrl;
}
