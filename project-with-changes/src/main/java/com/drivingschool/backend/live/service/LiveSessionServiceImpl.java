package com.drivingschool.backend.live.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.live.dto.AttendanceResponse;
import com.drivingschool.backend.live.dto.CreateLiveSessionRequest;
import com.drivingschool.backend.live.dto.LiveSessionResponse;
import com.drivingschool.backend.live.dto.RegisterAttendanceRequest;
import com.drivingschool.backend.live.entity.Attendance;
import com.drivingschool.backend.live.entity.LiveSession;
import com.drivingschool.backend.live.enums.AttendanceStatus;
import com.drivingschool.backend.live.enums.SessionStatus;
import com.drivingschool.backend.live.mapper.LiveSessionMapper;
import com.drivingschool.backend.live.repository.AttendanceRepository;
import com.drivingschool.backend.live.repository.LiveSessionRepository;
import com.drivingschool.backend.live.validator.LiveSessionValidator;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LiveSessionServiceImpl implements LiveSessionService {

    private final LiveSessionRepository liveSessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final SchoolRepository schoolRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LiveSessionMapper liveSessionMapper;
    private final LiveSessionValidator validator;

    public LiveSessionServiceImpl(LiveSessionRepository liveSessionRepository,
                                  AttendanceRepository attendanceRepository,
                                  InstructorProfileRepository instructorProfileRepository,
                                  SchoolRepository schoolRepository,
                                  StudentProfileRepository studentProfileRepository,
                                  LiveSessionMapper liveSessionMapper,
                                  LiveSessionValidator validator) {
        this.liveSessionRepository = liveSessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.schoolRepository = schoolRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.liveSessionMapper = liveSessionMapper;
        this.validator = validator;
    }

    private Long resolveCallerSchoolId(Long userId, String role) {
        if ("STUDENT".equals(role)) {
            return studentProfileRepository.findByUserId(userId).map(s -> s.getSchool().getId()).orElse(null);
        }
        if ("INSTRUCTOR".equals(role)) {
            return instructorProfileRepository.findByUserId(userId).map(i -> i.getSchool().getId()).orElse(null);
        }
        return null;
    }

    @Override
    @Transactional
    public LiveSessionResponse schedule(CreateLiveSessionRequest request, Long userId, String role) {
        InstructorProfile instructor = instructorProfileRepository.findById(request.getInstructorId())
                .orElseThrow(() -> new ResourceNotFoundException("InstructorProfile", "id", request.getInstructorId()));
        validator.validateInstructorSelf(instructor, userId, role);

        School school = schoolRepository.findById(request.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException("School", "id", request.getSchoolId()));

        LiveSession session = LiveSession.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .scheduledAt(request.getScheduledAt())
                .durationMinutes(request.getDurationMinutes())
                .meetingUrl(request.getMeetingUrl())
                .maxParticipants(request.getMaxParticipants())
                .status(SessionStatus.SCHEDULED)
                .instructor(instructor)
                .school(school)
                .build();

        LiveSession saved = liveSessionRepository.save(session);
        log.info("Live session scheduled: id={}, at={}", saved.getId(), saved.getScheduledAt());
        return liveSessionMapper.toResponse(saved, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public LiveSessionResponse getById(Long sessionId, Long userId, String role) {
        LiveSession session = findSession(sessionId);
        validator.validateSchoolAccess(session.getSchool().getId(), resolveCallerSchoolId(userId, role), role);
        int count = attendanceRepository.findBySessionId(sessionId).size();
        return liveSessionMapper.toResponse(session, count);
    }

    @Override
    @Transactional
    public LiveSessionResponse updateStatus(Long sessionId, SessionStatus status, Long userId, String role) {
        LiveSession session = findSession(sessionId);
        validator.validateInstructorOwnership(session, userId, role);
        session.updateStatus(status);
        LiveSession saved = liveSessionRepository.save(session);
        int count = attendanceRepository.findBySessionId(sessionId).size();
        return liveSessionMapper.toResponse(saved, count);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LiveSessionResponse> getUpcomingBySchool(Long schoolId, Long userId, String role) {
        validator.validateSchoolAccess(schoolId, resolveCallerSchoolId(userId, role), role);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekAhead = now.plusDays(7);
        List<LiveSession> sessions = liveSessionRepository.findBySchoolIdAndScheduledAtBetween(schoolId, now, weekAhead);

        List<Long> sessionIds = sessions.stream().map(LiveSession::getId).toList();
        Map<Long, Long> countsBySessionId = sessionIds.isEmpty()
                ? Map.of()
                : attendanceRepository.countBySessionIdIn(sessionIds).stream()
                        .collect(Collectors.toMap(AttendanceRepository.SessionAttendanceCount::getSessionId,
                                AttendanceRepository.SessionAttendanceCount::getAttendeeCount));

        return sessions.stream()
                .map(s -> liveSessionMapper.toResponse(s, countsBySessionId.getOrDefault(s.getId(), 0L).intValue()))
                .toList();
    }

    @Override
    @Transactional
    public AttendanceResponse register(Long sessionId, RegisterAttendanceRequest request, Long userId, String role) {
        LiveSession session = findSession(sessionId);
        if (session.getStatus() == SessionStatus.CANCELLED || session.getStatus() == SessionStatus.COMPLETED) {
            throw new BadRequestException("Cannot register for this session");
        }

        // STUDENT can only ever register themselves - request.getStudentId() is
        // client-supplied and must never be trusted for that role. ADMIN retains the
        // ability to register on behalf of any student.
        StudentProfile student = "ADMIN".equals(role)
                ? studentProfileRepository.findById(request.getStudentId())
                        .orElseThrow(() -> new ResourceNotFoundException("StudentProfile", "id", request.getStudentId()))
                : studentProfileRepository.findByUserId(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Student profile not found for user ID: " + userId));

        if (attendanceRepository.existsBySessionIdAndStudentId(sessionId, student.getId())) {
            throw new BadRequestException("Student already registered for this session");
        }

        if (session.getMaxParticipants() != null) {
            int current = attendanceRepository.findBySessionId(sessionId).size();
            if (current >= session.getMaxParticipants()) {
                throw new BadRequestException("Session is full");
            }
        }

        Attendance attendance = Attendance.builder()
                .session(session)
                .student(student)
                .status(AttendanceStatus.REGISTERED)
                .build();

        return liveSessionMapper.toAttendanceResponse(attendanceRepository.save(attendance));
    }

    @Override
    @Transactional
    public AttendanceResponse markPresent(Long sessionId, Long studentId, Long userId, String role) {
        Attendance attendance = attendanceRepository.findBySessionIdAndStudentId(sessionId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance", "studentId", studentId));
        validator.validateInstructorOwnership(attendance.getSession(), userId, role);
        attendance.markPresent();
        return liveSessionMapper.toAttendanceResponse(attendanceRepository.save(attendance));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendance(Long sessionId, Long userId, String role) {
        LiveSession session = findSession(sessionId);
        validator.validateInstructorOwnership(session, userId, role);
        return attendanceRepository.findBySessionId(sessionId).stream()
                .map(liveSessionMapper::toAttendanceResponse)
                .toList();
    }

    private LiveSession findSession(Long sessionId) {
        return liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("LiveSession", "id", sessionId));
    }
}
