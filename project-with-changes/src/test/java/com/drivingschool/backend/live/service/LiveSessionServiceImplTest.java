package com.drivingschool.backend.live.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
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
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveSessionServiceImplTest {

    private final AdminSchoolScope adminSchoolScope = mock(AdminSchoolScope.class);

    @Mock private LiveSessionRepository liveSessionRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    @Mock private SchoolRepository schoolRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    private final LiveSessionMapper liveSessionMapper = new LiveSessionMapper();
    private final LiveSessionValidator validator = new LiveSessionValidator(adminSchoolScope);

    private LiveSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LiveSessionServiceImpl(liveSessionRepository, attendanceRepository,
                instructorProfileRepository, schoolRepository, studentProfileRepository,
                liveSessionMapper, validator);
    }

    private User userWithId(Long id) {
        User user = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private School schoolWithId(Long id) {
        School school = School.builder().active(true).build();
        ReflectionTestUtils.setField(school, "id", id);
        return school;
    }

    private InstructorProfile instructorProfile(Long profileId, User user, School school) {
        InstructorProfile instructor = InstructorProfile.builder().user(user).active(true).school(school).build();
        ReflectionTestUtils.setField(instructor, "id", profileId);
        return instructor;
    }

    private StudentProfile studentProfile(Long profileId, User user, School school) {
        StudentProfile student = StudentProfile.builder().user(user).school(school).build();
        ReflectionTestUtils.setField(student, "id", profileId);
        return student;
    }

    private LiveSession sessionFor(InstructorProfile instructor, School school) {
        LiveSession session = LiveSession.builder().title("Intro").instructor(instructor).school(school)
                .scheduledAt(LocalDateTime.now().plusDays(1)).durationMinutes(60)
                .meetingUrl("https://meet.example.com/intro")
                .status(SessionStatus.SCHEDULED).build();
        ReflectionTestUtils.setField(session, "id", 30L);
        return session;
    }

    // --- getById: cross-tenant leak fix ---

    @Test
    void getById_asStudentAtSameSchool_isAllowed() {
        School school = schoolWithId(5L);
        LiveSession session = sessionFor(instructorProfile(50L, userWithId(1L), school), school);
        StudentProfile caller = studentProfile(60L, userWithId(2L), school);

        when(liveSessionRepository.findById(30L)).thenReturn(Optional.of(session));
        when(attendanceRepository.findBySessionId(30L)).thenReturn(List.of());
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(caller));

        assertThatCode(() -> service.getById(30L, 2L, "STUDENT")).doesNotThrowAnyException();
    }

    @Test
    void getById_asStudentAtDifferentSchool_isDenied() {
        School sessionSchool = schoolWithId(5L);
        School callerSchool = schoolWithId(6L);
        LiveSession session = sessionFor(instructorProfile(50L, userWithId(1L), sessionSchool), sessionSchool);
        StudentProfile caller = studentProfile(60L, userWithId(2L), callerSchool);

        when(liveSessionRepository.findById(30L)).thenReturn(Optional.of(session));
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> service.getById(30L, 2L, "STUDENT"))
                .isInstanceOf(ForbiddenException.class);
    }

    // --- schedule: instructor identity-spoofing fix ---

    @Test
    void schedule_asDifferentInstructor_isDenied() {
        School school = schoolWithId(5L);
        InstructorProfile targetInstructor = instructorProfile(50L, userWithId(1L), school);
        var request = com.drivingschool.backend.live.dto.CreateLiveSessionRequest.builder()
                .instructorId(50L).schoolId(5L).title("Intro")
                .scheduledAt(java.time.LocalDateTime.now().plusDays(1)).durationMinutes(60).build();

        when(instructorProfileRepository.findById(50L)).thenReturn(Optional.of(targetInstructor));

        assertThatThrownBy(() -> service.schedule(request, 999L, "INSTRUCTOR"))
                .isInstanceOf(ForbiddenException.class);

        verify(liveSessionRepository, never()).save(any());
    }

    // --- register: student identity-spoofing fix ---

    @Test
    void register_asStudent_ignoresRequestBodyStudentIdAndUsesCallerIdentity() {
        School school = schoolWithId(5L);
        LiveSession session = sessionFor(instructorProfile(50L, userWithId(1L), school), school);
        StudentProfile callingStudent = studentProfile(20L, userWithId(2L), school);
        RegisterAttendanceRequest request = RegisterAttendanceRequest.builder().studentId(60L).build();

        when(liveSessionRepository.findById(30L)).thenReturn(Optional.of(session));
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(callingStudent));
        when(attendanceRepository.existsBySessionIdAndStudentId(30L, 20L)).thenReturn(false);
        when(attendanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.register(30L, request, 2L, "STUDENT");

        assertThat(response.getStudentId()).isEqualTo(20L);
        verify(studentProfileRepository, never()).findById(60L);
    }

    // --- markPresent / getAttendance: instructor ownership fix ---

    @Test
    void markPresent_asUnrelatedInstructor_isDenied() {
        School school = schoolWithId(5L);
        LiveSession session = sessionFor(instructorProfile(50L, userWithId(1L), school), school);
        Attendance attendance = Attendance.builder().session(session)
                .student(studentProfile(20L, userWithId(2L), school)).status(AttendanceStatus.REGISTERED).build();

        when(attendanceRepository.findBySessionIdAndStudentId(30L, 20L)).thenReturn(Optional.of(attendance));

        assertThatThrownBy(() -> service.markPresent(30L, 20L, 999L, "INSTRUCTOR"))
                .isInstanceOf(ForbiddenException.class);

        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void getAttendance_asOwningInstructor_isAllowed() {
        School school = schoolWithId(5L);
        LiveSession session = sessionFor(instructorProfile(50L, userWithId(1L), school), school);

        when(liveSessionRepository.findById(30L)).thenReturn(Optional.of(session));
        when(attendanceRepository.findBySessionId(30L)).thenReturn(List.of());

        assertThatCode(() -> service.getAttendance(30L, 1L, "INSTRUCTOR")).doesNotThrowAnyException();
    }

    @Test
    void getAttendance_asUnrelatedInstructor_isDenied() {
        School school = schoolWithId(5L);
        LiveSession session = sessionFor(instructorProfile(50L, userWithId(1L), school), school);

        when(liveSessionRepository.findById(30L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.getAttendance(30L, 999L, "INSTRUCTOR"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void register_asAdminOfAnotherSchool_isDenied() {
        School school = schoolWithId(5L);
        LiveSession session = sessionFor(instructorProfile(50L, userWithId(1L), school), school);
        RegisterAttendanceRequest request = RegisterAttendanceRequest.builder().studentId(60L).build();

        when(liveSessionRepository.findById(30L)).thenReturn(Optional.of(session));
        when(studentProfileRepository.findById(60L)).thenReturn(Optional.of(studentProfile(60L, userWithId(6L), school)));
        doThrow(new BadRequestException("no access")).when(adminSchoolScope).requireAccess(5L);

        assertThatThrownBy(() -> service.register(30L, request, 999L, "ADMIN"))
                .isInstanceOf(BadRequestException.class);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void register_asAdmin_studentFromAnotherSchool_isDenied() {
        School sessionSchool = schoolWithId(5L);
        LiveSession session = sessionFor(instructorProfile(50L, userWithId(1L), sessionSchool), sessionSchool);
        RegisterAttendanceRequest request = RegisterAttendanceRequest.builder().studentId(60L).build();

        when(liveSessionRepository.findById(30L)).thenReturn(Optional.of(session));
        when(studentProfileRepository.findById(60L))
                .thenReturn(Optional.of(studentProfile(60L, userWithId(6L), schoolWithId(6L))));

        assertThatThrownBy(() -> service.register(30L, request, 999L, "ADMIN"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("does not belong to this session's school");
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void register_asStudentOfAnotherSchool_isDenied() {
        School sessionSchool = schoolWithId(5L);
        LiveSession session = sessionFor(instructorProfile(50L, userWithId(1L), sessionSchool), sessionSchool);
        RegisterAttendanceRequest request = RegisterAttendanceRequest.builder().build();

        when(liveSessionRepository.findById(30L)).thenReturn(Optional.of(session));
        when(studentProfileRepository.findByUserId(2L))
                .thenReturn(Optional.of(studentProfile(20L, userWithId(2L), schoolWithId(6L))));

        assertThatThrownBy(() -> service.register(30L, request, 2L, "STUDENT"))
                .isInstanceOf(ForbiddenException.class);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void schedule_atAnotherSchoolThanTheInstructors_isDenied() {
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L), schoolWithId(5L));
        var request = com.drivingschool.backend.live.dto.CreateLiveSessionRequest.builder()
                .instructorId(50L).schoolId(6L).title("Intro")
                .scheduledAt(java.time.LocalDateTime.now().plusDays(1)).durationMinutes(60).build();

        when(instructorProfileRepository.findById(50L)).thenReturn(Optional.of(instructor));

        assertThatThrownBy(() -> service.schedule(request, 1L, "INSTRUCTOR"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("instructor's own school");
        verify(liveSessionRepository, never()).save(any());
    }

    // --- meeting link gating, registration window, unregister ---

    @Test
    void meetingUrl_isHiddenFromAStudentWhoHasNotRegistered() {
        School school = schoolWithId(5L);
        LiveSession session = sessionFor(instructorProfile(50L, userWithId(1L), school), school);
        when(liveSessionRepository.findById(30L)).thenReturn(Optional.of(session));
        when(attendanceRepository.findBySessionId(30L)).thenReturn(List.of());
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(studentProfile(60L, userWithId(2L), school)));
        when(attendanceRepository.existsBySessionIdAndStudentId(30L, 60L)).thenReturn(false);

        var response = service.getById(30L, 2L, "STUDENT");

        assertThat(response.getMeetingUrl()).isNull();
        assertThat(response.getRegistered()).isFalse();
        assertThat(response.getEndsAt()).isEqualTo(session.getScheduledAt().plusMinutes(60));
    }

    @Test
    void meetingUrl_isShownToARegisteredStudent() {
        School school = schoolWithId(5L);
        LiveSession session = sessionFor(instructorProfile(50L, userWithId(1L), school), school);
        when(liveSessionRepository.findById(30L)).thenReturn(Optional.of(session));
        when(attendanceRepository.findBySessionId(30L)).thenReturn(List.of());
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(studentProfile(60L, userWithId(2L), school)));
        when(attendanceRepository.existsBySessionIdAndStudentId(30L, 60L)).thenReturn(true);

        var response = service.getById(30L, 2L, "STUDENT");

        assertThat(response.getMeetingUrl()).isEqualTo("https://meet.example.com/intro");
        assertThat(response.getRegistered()).isTrue();
    }

    @Test
    void meetingUrl_isAlwaysShownToTheInstructor_withNoRegisteredFlag() {
        School school = schoolWithId(5L);
        LiveSession session = sessionFor(instructorProfile(50L, userWithId(1L), school), school);
        when(liveSessionRepository.findById(30L)).thenReturn(Optional.of(session));
        when(attendanceRepository.findBySessionId(30L)).thenReturn(List.of());
        when(instructorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(session.getInstructor()));

        var response = service.getById(30L, 1L, "INSTRUCTOR");

        assertThat(response.getMeetingUrl()).isEqualTo("https://meet.example.com/intro");
        assertThat(response.getRegistered()).isNull();
    }

    @Test
    void upcoming_keepsASessionThatIsUnderWay_dropsOneThatHasEnded_andFlagsRegistration() {
        School school = schoolWithId(5L);
        InstructorProfile instructor = instructorProfile(50L, userWithId(1L), school);
        LiveSession running = LiveSession.builder().title("Running").instructor(instructor).school(school)
                .scheduledAt(LocalDateTime.now().minusMinutes(30)).durationMinutes(60)
                .meetingUrl("https://meet.example.com/running").status(SessionStatus.IN_PROGRESS).build();
        ReflectionTestUtils.setField(running, "id", 31L);
        LiveSession ended = LiveSession.builder().title("Ended").instructor(instructor).school(school)
                .scheduledAt(LocalDateTime.now().minusHours(3)).durationMinutes(60)
                .meetingUrl("https://meet.example.com/ended").status(SessionStatus.SCHEDULED).build();
        ReflectionTestUtils.setField(ended, "id", 32L);
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(studentProfile(60L, userWithId(2L), school)));
        when(liveSessionRepository.findBySchoolIdAndScheduledAtBetween(any(), any(), any())).thenReturn(List.of(running, ended));
        when(attendanceRepository.countBySessionIdIn(List.of(31L))).thenReturn(List.of());
        when(attendanceRepository.findRegisteredSessionIds(60L, List.of(31L))).thenReturn(List.of(31L));

        var sessions = service.getUpcomingBySchool(5L, 2L, "STUDENT");

        assertThat(sessions).singleElement().satisfies(s -> {
            assertThat(s.getTitle()).isEqualTo("Running");
            assertThat(s.getRegistered()).isTrue();
            assertThat(s.getMeetingUrl()).isEqualTo("https://meet.example.com/running");
        });
    }

    @Test
    void register_returnsTheMeetingUrl_andIsAllowedWhileTheSessionIsRunning() {
        School school = schoolWithId(5L);
        LiveSession session = sessionFor(instructorProfile(50L, userWithId(1L), school), school);
        ReflectionTestUtils.setField(session, "scheduledAt", LocalDateTime.now().minusMinutes(10));
        when(liveSessionRepository.findById(30L)).thenReturn(Optional.of(session));
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(studentProfile(20L, userWithId(2L), school)));
        when(attendanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.register(30L, RegisterAttendanceRequest.builder().build(), 2L, "STUDENT");

        assertThat(response.getMeetingUrl()).isEqualTo("https://meet.example.com/intro");
    }

    @Test
    void register_afterTheSessionHasEnded_isRejected() {
        School school = schoolWithId(5L);
        LiveSession session = sessionFor(instructorProfile(50L, userWithId(1L), school), school);
        ReflectionTestUtils.setField(session, "scheduledAt", LocalDateTime.now().minusHours(2));
        when(liveSessionRepository.findById(30L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.register(30L, RegisterAttendanceRequest.builder().build(), 2L, "STUDENT"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("This session has already ended");
    }

    @Test
    void unregister_removesTheStudentsRegistration() {
        School school = schoolWithId(5L);
        LiveSession session = sessionFor(instructorProfile(50L, userWithId(1L), school), school);
        StudentProfile student = studentProfile(20L, userWithId(2L), school);
        Attendance attendance = Attendance.builder().session(session).student(student).status(AttendanceStatus.REGISTERED).build();
        when(liveSessionRepository.findById(30L)).thenReturn(Optional.of(session));
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student));
        when(attendanceRepository.findBySessionIdAndStudentId(30L, 20L)).thenReturn(Optional.of(attendance));

        service.unregister(30L, 2L);

        verify(attendanceRepository).delete(attendance);
    }

    @Test
    void unregister_afterBeingMarkedPresent_isRejected() {
        School school = schoolWithId(5L);
        LiveSession session = sessionFor(instructorProfile(50L, userWithId(1L), school), school);
        StudentProfile student = studentProfile(20L, userWithId(2L), school);
        Attendance attendance = Attendance.builder().session(session).student(student).status(AttendanceStatus.PRESENT).build();
        when(liveSessionRepository.findById(30L)).thenReturn(Optional.of(session));
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student));
        when(attendanceRepository.findBySessionIdAndStudentId(30L, 20L)).thenReturn(Optional.of(attendance));

        assertThatThrownBy(() -> service.unregister(30L, 2L)).isInstanceOf(BadRequestException.class);
        verify(attendanceRepository, never()).delete(any());
    }
}
