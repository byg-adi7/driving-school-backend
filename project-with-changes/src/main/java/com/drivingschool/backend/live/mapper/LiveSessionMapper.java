package com.drivingschool.backend.live.mapper;

import com.drivingschool.backend.live.dto.AttendanceResponse;
import com.drivingschool.backend.live.dto.LiveSessionResponse;
import com.drivingschool.backend.live.entity.Attendance;
import com.drivingschool.backend.live.entity.LiveSession;
import org.springframework.stereotype.Component;

@Component
public class LiveSessionMapper {

    /** For instructors and admins: always the meeting link, no "registered" flag. */
    public LiveSessionResponse toResponse(LiveSession session, int registeredCount) {
        return toResponse(session, registeredCount, null);
    }

    /**
     * @param studentRegistered null for instructors/admins; for a student, whether they're
     *                          registered - the meeting link is only shown when they are.
     */
    public LiveSessionResponse toResponse(LiveSession session, int registeredCount, Boolean studentRegistered) {
        boolean showMeetingUrl = studentRegistered == null || studentRegistered;
        return LiveSessionResponse.builder()
                .id(session.getId())
                .title(session.getTitle())
                .description(session.getDescription())
                .scheduledAt(session.getScheduledAt())
                .durationMinutes(session.getDurationMinutes())
                .meetingUrl(showMeetingUrl ? session.getMeetingUrl() : null)
                .maxParticipants(session.getMaxParticipants())
                .status(session.getStatus())
                .instructorId(session.getInstructor().getId())
                .instructorName(session.getInstructor().getFirstName() + " " + session.getInstructor().getLastName())
                .schoolId(session.getSchool().getId())
                .registeredCount(registeredCount)
                .registered(studentRegistered)
                .endsAt(session.getEndsAt())
                .build();
    }

    public AttendanceResponse toAttendanceResponse(Attendance attendance) {
        return AttendanceResponse.builder()
                .id(attendance.getId())
                .sessionId(attendance.getSession().getId())
                .studentId(attendance.getStudent().getId())
                .studentName(attendance.getStudent().getFirstName() + " " + attendance.getStudent().getLastName())
                .status(attendance.getStatus())
                .checkedInAt(attendance.getCheckedInAt())
                .build();
    }
}
