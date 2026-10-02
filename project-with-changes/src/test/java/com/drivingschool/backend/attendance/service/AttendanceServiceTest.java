package com.drivingschool.backend.attendance.service;

import com.drivingschool.backend.attendance.dto.CheckInRequest;
import com.drivingschool.backend.attendance.dto.DailyAttendanceResponse;
import com.drivingschool.backend.attendance.dto.ManualAttendanceRequest;
import com.drivingschool.backend.attendance.dto.UpdateSchoolLocationRequest;
import com.drivingschool.backend.attendance.entity.DailyAttendance;
import com.drivingschool.backend.attendance.enums.AttendanceSource;
import com.drivingschool.backend.attendance.enums.DailyAttendanceStatus;
import com.drivingschool.backend.attendance.repository.DailyAttendanceRepository;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.DetailedBadRequestException;
import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.entity.School;
import com.drivingschool.backend.school.mapper.SchoolMapper;
import com.drivingschool.backend.school.repository.SchoolRepository;
import com.drivingschool.backend.school.validator.AdminSchoolScope;
import com.drivingschool.backend.security.CurrentUserService;
import com.drivingschool.backend.student.entity.StudentProfile;
import com.drivingschool.backend.student.enums.StudentStatus;
import com.drivingschool.backend.student.repository.StudentProfileRepository;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    // Accra; ~0.0005 degrees of latitude is ~55 m.
    private static final double SCHOOL_LAT = 5.6037;
    private static final double SCHOOL_LON = -0.1870;
    // 2 Oct 2026, 09:00 UTC (= 09:00 in Africa/Accra).
    private static final Instant NOW = Instant.parse("2026-10-02T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    @Mock private DailyAttendanceRepository attendanceRepository;
    @Mock private SchoolRepository schoolRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private InstructorProfileRepository instructorProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private AdminSchoolScope adminSchoolScope;

    private AttendanceService service;
    private School school;

    @BeforeEach
    void setUp() {
        service = new AttendanceService(attendanceRepository, schoolRepository, studentProfileRepository,
                instructorProfileRepository, userRepository, currentUserService, adminSchoolScope,
                new SchoolMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        school = schoolWithId(5L);
        school.updateAttendanceSettings(SCHOOL_LAT, SCHOOL_LON, 150, "Africa/Accra");
        lenient().when(attendanceRepository.save(any(DailyAttendance.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private School schoolWithId(Long id) {
        School s = School.builder().name("Aidly Driving").address("Accra").active(true).build();
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private User user(Long id) {
        User u = User.builder().email("u" + id + "@example.com").password("x").enabled(true).emailVerified(true).build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private StudentProfile student(Long userId, String first, String last, School at) {
        return StudentProfile.builder().firstName(first).lastName(last).status(StudentStatus.ACTIVE)
                .school(at).user(user(userId)).build();
    }

    private InstructorProfile instructor(Long userId, School at) {
        return InstructorProfile.builder().firstName("Ina").lastName("Instructor").active(true)
                .school(at).user(user(userId)).build();
    }

    private void callerIs(Long userId, RoleName role) {
        lenient().when(currentUserService.requireUserId()).thenReturn(userId);
        lenient().when(currentUserService.hasRole(any())).thenAnswer(inv -> inv.getArgument(0) == role);
    }

    private CheckInRequest at(double lat, double lon, double accuracy) {
        return CheckInRequest.builder().latitude(lat).longitude(lon).accuracyMeters(accuracy).build();
    }

    // ------------------------------------------------------------------ check-in

    @Test
    void aStudentCheckingInAtSchool_isPendingConfirmation_withTheDistanceKept() {
        callerIs(2L, RoleName.STUDENT);
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student(2L, "Ama", "Mensah", school)));

        DailyAttendanceResponse response = service.checkIn(at(SCHOOL_LAT + 0.0005, SCHOOL_LON, 12));

        assertThat(response.getStatus()).isEqualTo(DailyAttendanceStatus.PENDING_CONFIRMATION);
        assertThat(response.getDate()).isEqualTo(TODAY);
        assertThat(response.getDistanceMeters()).isBetween(50.0, 60.0);
        assertThat(response.getSource()).isEqualTo(AttendanceSource.CHECK_IN);
    }

    @Test
    void anInstructorCheckingIn_isPresentStraightAway() {
        callerIs(3L, RoleName.INSTRUCTOR);
        when(studentProfileRepository.findByUserId(3L)).thenReturn(Optional.empty());
        when(instructorProfileRepository.findByUserId(3L)).thenReturn(Optional.of(instructor(3L, school)));

        assertThat(service.checkIn(at(SCHOOL_LAT, SCHOOL_LON, 10)).getStatus()).isEqualTo(DailyAttendanceStatus.PRESENT);
    }

    @Test
    void checkingInTooFarAway_isRejected_withTheDistanceInTheResponse() {
        callerIs(2L, RoleName.STUDENT);
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student(2L, "Ama", "Mensah", school)));

        assertThatThrownBy(() -> service.checkIn(at(SCHOOL_LAT + 0.01, SCHOOL_LON, 10)))
                .isInstanceOf(DetailedBadRequestException.class)
                .satisfies(ex -> {
                    var details = ((DetailedBadRequestException) ex).getDetails();
                    assertThat((Double) details.get("distanceMeters")).isBetween(1100.0, 1120.0);
                    assertThat(details.get("radiusMeters")).isEqualTo(150);
                });
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void anImpreciseLocation_isRejected() {
        callerIs(2L, RoleName.STUDENT);
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student(2L, "Ama", "Mensah", school)));

        assertThatThrownBy(() -> service.checkIn(at(SCHOOL_LAT, SCHOOL_LON, 450)))
                .isInstanceOf(DetailedBadRequestException.class)
                .hasMessageContaining("isn't precise enough");
    }

    @Test
    void checkingIn_beforeTheSchoolHasALocation_isRejected() {
        School noLocation = schoolWithId(6L);
        callerIs(2L, RoleName.STUDENT);
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student(2L, "Ama", "Mensah", noLocation)));

        assertThatThrownBy(() -> service.checkIn(at(SCHOOL_LAT, SCHOOL_LON, 10)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("hasn't set its attendance location");
    }

    @Test
    void checkingInTwiceInADay_isRejected() {
        callerIs(2L, RoleName.STUDENT);
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student(2L, "Ama", "Mensah", school)));
        when(attendanceRepository.findByUserIdAndAttendanceDate(2L, TODAY))
                .thenReturn(Optional.of(DailyAttendance.builder().build()));

        assertThatThrownBy(() -> service.checkIn(at(SCHOOL_LAT, SCHOOL_LON, 10)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You've already checked in today");
    }

    @Test
    void theDayIsTheSchoolsLocalDay_notTheServers() {
        // 23:30 UTC on 2 Oct is already 3 Oct in Auckland (UTC+13).
        service = new AttendanceService(attendanceRepository, schoolRepository, studentProfileRepository,
                instructorProfileRepository, userRepository, currentUserService, adminSchoolScope,
                new SchoolMapper(), Clock.fixed(Instant.parse("2026-10-02T23:30:00Z"), ZoneOffset.UTC));
        school.updateAttendanceSettings(SCHOOL_LAT, SCHOOL_LON, 150, "Pacific/Auckland");
        callerIs(2L, RoleName.STUDENT);
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student(2L, "Ama", "Mensah", school)));

        assertThat(service.checkIn(at(SCHOOL_LAT, SCHOOL_LON, 10)).getDate()).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    @Test
    void anAdmin_hasNoAttendanceToCheckIn() {
        callerIs(9L, RoleName.ADMIN);
        when(studentProfileRepository.findByUserId(9L)).thenReturn(Optional.empty());
        when(instructorProfileRepository.findByUserId(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkIn(at(SCHOOL_LAT, SCHOOL_LON, 10))).isInstanceOf(ForbiddenException.class);
    }

    // ------------------------------------------------------------------ day list

    @Test
    void theDayList_includesEveryActiveStudent_withNotCheckedInForTodayAndAbsentForPastDays() {
        callerIs(9L, RoleName.ADMIN);
        when(schoolRepository.findById(5L)).thenReturn(Optional.of(school));
        StudentProfile ama = student(2L, "Ama", "Mensah", school);
        StudentProfile kofi = student(4L, "Kofi", "Boateng", school);
        when(studentProfileRepository.findBySchoolIdExcludingDeletedUsers(5L)).thenReturn(List.of(ama, kofi));
        DailyAttendance amaToday = DailyAttendance.builder().school(school).user(ama.getUser()).role(RoleName.STUDENT)
                .attendanceDate(TODAY).status(DailyAttendanceStatus.PENDING_CONFIRMATION).source(AttendanceSource.CHECK_IN).build();
        when(attendanceRepository.findForSchool(5L, RoleName.STUDENT, TODAY, TODAY)).thenReturn(List.of(amaToday));
        when(attendanceRepository.findForSchool(5L, RoleName.STUDENT, TODAY.minusDays(1), TODAY.minusDays(1))).thenReturn(List.of());

        List<DailyAttendanceResponse> today = service.schoolDay(5L, null, null);
        List<DailyAttendanceResponse> yesterday = service.schoolDay(5L, TODAY.minusDays(1), RoleName.STUDENT);

        // Sorted by last name: Boateng, Mensah.
        assertThat(today).extracting(DailyAttendanceResponse::getName).containsExactly("Kofi Boateng", "Ama Mensah");
        assertThat(today).extracting(DailyAttendanceResponse::getStatus)
                .containsExactly(DailyAttendanceStatus.NOT_CHECKED_IN, DailyAttendanceStatus.PENDING_CONFIRMATION);
        assertThat(yesterday).extracting(DailyAttendanceResponse::getStatus)
                .containsOnly(DailyAttendanceStatus.ABSENT);
    }

    @Test
    void anInstructorOfAnotherSchool_cannotSeeTheDayList() {
        callerIs(3L, RoleName.INSTRUCTOR);
        when(instructorProfileRepository.findByUserId(3L)).thenReturn(Optional.of(instructor(3L, schoolWithId(99L))));

        assertThatThrownBy(() -> service.schoolDay(5L, null, null)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aRangeLongerThan62Days_isRejected() {
        callerIs(9L, RoleName.ADMIN);
        when(schoolRepository.findById(5L)).thenReturn(Optional.of(school));

        assertThatThrownBy(() -> service.register(5L, TODAY.minusDays(70), TODAY, RoleName.STUDENT))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at most 62 days");
    }

    // ------------------------------------------------------------------ staff actions

    @Test
    void anInstructorOfTheSchool_confirmsAPendingCheckIn() {
        callerIs(3L, RoleName.INSTRUCTOR);
        StudentProfile ama = student(2L, "Ama", "Mensah", school);
        DailyAttendance pending = DailyAttendance.builder().school(school).user(ama.getUser()).role(RoleName.STUDENT)
                .attendanceDate(TODAY).status(DailyAttendanceStatus.PENDING_CONFIRMATION).source(AttendanceSource.CHECK_IN).build();
        InstructorProfile ina = instructor(3L, school);
        when(attendanceRepository.findById(40L)).thenReturn(Optional.of(pending));
        when(instructorProfileRepository.findByUserId(3L)).thenReturn(Optional.of(ina));
        when(userRepository.findById(3L)).thenReturn(Optional.of(ina.getUser()));
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(ama));

        DailyAttendanceResponse confirmed = service.confirm(40L);

        assertThat(confirmed.getStatus()).isEqualTo(DailyAttendanceStatus.PRESENT);
        assertThat(confirmed.getConfirmedAt()).isNotNull();
    }

    @Test
    void confirmingSomethingNotPending_isRejected() {
        callerIs(9L, RoleName.ADMIN);
        DailyAttendance present = DailyAttendance.builder().school(school).user(user(2L)).role(RoleName.STUDENT)
                .attendanceDate(TODAY).status(DailyAttendanceStatus.PRESENT).source(AttendanceSource.MANUAL).build();
        when(attendanceRepository.findById(40L)).thenReturn(Optional.of(present));

        assertThatThrownBy(() -> service.confirm(40L)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void anInstructor_recordsAStudentLate_correctingAnExistingCheckIn() {
        callerIs(3L, RoleName.INSTRUCTOR);
        StudentProfile ama = student(2L, "Ama", "Mensah", school);
        InstructorProfile ina = instructor(3L, school);
        DailyAttendance existing = DailyAttendance.builder().school(school).user(ama.getUser()).role(RoleName.STUDENT)
                .attendanceDate(TODAY).status(DailyAttendanceStatus.PENDING_CONFIRMATION).source(AttendanceSource.CHECK_IN).build();
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(ama));
        when(instructorProfileRepository.findByUserId(3L)).thenReturn(Optional.of(ina));
        when(userRepository.findById(3L)).thenReturn(Optional.of(ina.getUser()));
        when(attendanceRepository.findByUserIdAndAttendanceDate(2L, TODAY)).thenReturn(Optional.of(existing));

        DailyAttendanceResponse late = service.recordManually(ManualAttendanceRequest.builder()
                .userId(2L).date(TODAY).status(DailyAttendanceStatus.LATE).reason("Arrived 09:40").build());

        assertThat(late.getStatus()).isEqualTo(DailyAttendanceStatus.LATE);
        assertThat(late.getSource()).isEqualTo(AttendanceSource.MANUAL);
        assertThat(late.getReason()).isEqualTo("Arrived 09:40");
    }

    @Test
    void anInstructor_cannotRecordAnotherInstructorsAttendance() {
        callerIs(3L, RoleName.INSTRUCTOR);
        when(studentProfileRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(instructorProfileRepository.findByUserId(7L)).thenReturn(Optional.of(instructor(7L, school)));

        assertThatThrownBy(() -> service.recordManually(ManualAttendanceRequest.builder()
                .userId(7L).date(TODAY).status(DailyAttendanceStatus.ABSENT).build()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void recordingAFutureDate_isRejected() {
        callerIs(9L, RoleName.ADMIN);
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student(2L, "Ama", "Mensah", school)));

        assertThatThrownBy(() -> service.recordManually(ManualAttendanceRequest.builder()
                .userId(2L).date(TODAY.plusDays(1)).status(DailyAttendanceStatus.ABSENT).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("future");
    }

    @Test
    void pendingIsNotAManualStatus() {
        assertThatThrownBy(() -> service.recordManually(ManualAttendanceRequest.builder()
                .userId(2L).date(TODAY).status(DailyAttendanceStatus.PENDING_CONFIRMATION).build()))
                .isInstanceOf(BadRequestException.class);
    }

    // ------------------------------------------------------------------ school settings

    @Test
    void anAdmin_setsTheirSchoolsLocation_andAnUnknownTimeZoneIsRejected() {
        when(adminSchoolScope.restrictedSchoolId()).thenReturn(Optional.of(5L));
        when(schoolRepository.findById(5L)).thenReturn(Optional.of(school));
        when(schoolRepository.save(school)).thenReturn(school);

        var saved = service.updateSchoolLocation(null, UpdateSchoolLocationRequest.builder()
                .latitude(6.0).longitude(-0.2).attendanceRadiusMeters(200).build());
        assertThat(saved.getLatitude()).isEqualTo(6.0);
        assertThat(saved.getAttendanceRadiusMeters()).isEqualTo(200);
        assertThat(saved.getTimeZone()).isEqualTo("Africa/Accra");

        assertThatThrownBy(() -> service.updateSchoolLocation(null, UpdateSchoolLocationRequest.builder()
                .latitude(6.0).longitude(-0.2).timeZone("Mars/Olympus").build()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void distance_isAboutOneDegreeOfLatitude() {
        assertThat(AttendanceService.distanceMeters(5.0, -0.2, 6.0, -0.2)).isBetween(110_000.0, 112_000.0);
    }

    @Test
    void savedCheckIns_storeTheLocationForTheAuditTrail() {
        callerIs(2L, RoleName.STUDENT);
        when(studentProfileRepository.findByUserId(2L)).thenReturn(Optional.of(student(2L, "Ama", "Mensah", school)));

        service.checkIn(at(SCHOOL_LAT + 0.0003, SCHOOL_LON, 8));

        ArgumentCaptor<DailyAttendance> saved = ArgumentCaptor.forClass(DailyAttendance.class);
        verify(attendanceRepository).save(saved.capture());
        assertThat(saved.getValue().getLatitude()).isEqualTo(SCHOOL_LAT + 0.0003);
        assertThat(saved.getValue().getAccuracyMeters()).isEqualTo(8.0);
        assertThat(saved.getValue().getCheckedInAt()).isNotNull();
    }
}
