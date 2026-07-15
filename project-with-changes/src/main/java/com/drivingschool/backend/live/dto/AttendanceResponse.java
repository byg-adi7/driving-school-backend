package com.drivingschool.backend.live.dto;

import com.drivingschool.backend.live.enums.AttendanceStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AttendanceResponse {

    private final Long id;
    private final Long sessionId;
    private final Long studentId;
    private final String studentName;
    private final AttendanceStatus status;
    private final LocalDateTime checkedInAt;
}
