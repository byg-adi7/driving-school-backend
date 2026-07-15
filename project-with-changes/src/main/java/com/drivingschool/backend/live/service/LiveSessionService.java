package com.drivingschool.backend.live.service;

import com.drivingschool.backend.live.dto.AttendanceResponse;
import com.drivingschool.backend.live.dto.CreateLiveSessionRequest;
import com.drivingschool.backend.live.dto.LiveSessionResponse;
import com.drivingschool.backend.live.dto.RegisterAttendanceRequest;
import com.drivingschool.backend.live.enums.SessionStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface LiveSessionService {

    LiveSessionResponse schedule(CreateLiveSessionRequest request, Long userId, String role);

    LiveSessionResponse getById(Long sessionId, Long userId, String role);

    LiveSessionResponse updateStatus(Long sessionId, SessionStatus status, Long userId, String role);

    List<LiveSessionResponse> getUpcomingBySchool(Long schoolId, Long userId, String role);

    AttendanceResponse register(Long sessionId, RegisterAttendanceRequest request, Long userId, String role);

    AttendanceResponse markPresent(Long sessionId, Long studentId, Long userId, String role);

    List<AttendanceResponse> getAttendance(Long sessionId, Long userId, String role);
}
