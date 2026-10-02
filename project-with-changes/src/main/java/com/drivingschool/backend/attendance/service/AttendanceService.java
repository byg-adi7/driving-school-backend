package com.drivingschool.backend.attendance.service;

import com.drivingschool.backend.attendance.dto.CheckInRequest;
import com.drivingschool.backend.attendance.dto.DailyAttendanceResponse;
import com.drivingschool.backend.attendance.dto.ManualAttendanceRequest;
import com.drivingschool.backend.attendance.dto.UpdateSchoolLocationRequest;
import com.drivingschool.backend.attendance.entity.DailyAttendance;
import com.drivingschool.backend.attendance.enums.AttendanceSource;
import com.drivingschool.backend.attendance.enums.ConfirmationReason;
import com.drivingschool.backend.attendance.enums.DailyAttendanceStatus;
import com.drivingschool.backend.attendance.repository.DailyAttendanceRepository;
import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.DetailedBadRequestException;
import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.instructor.entity.InstructorProfile;
import com.drivingschool.backend.instructor.repository.InstructorProfileRepository;
import com.drivingschool.backend.role.enums.RoleName;
import com.drivingschool.backend.school.dto.SchoolResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Daily attendance: students and instructors check in once per school-local day from
 * their phone's location; staff confirm student check-ins and record or correct entries.
 */
@Service
public class AttendanceService {

    /** A fix less precise than this can't tell "at school" from "down the road". */
    static final double MAX_ACCURACY_METERS = 100;
    static final int MAX_RANGE_DAYS = 62;
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;
    private static final Set<DailyAttendanceStatus> MANUAL_STATUSES =
            Set.of(DailyAttendanceStatus.PRESENT, DailyAttendanceStatus.LATE, DailyAttendanceStatus.ABSENT);

    /** A person attendance is kept for, as staff see them on a list or register. */
    public record Person(Long userId, String name, RoleName role) {
    }

    public record DayList(School school, LocalDate date, RoleName role, List<DailyAttendanceResponse> rows) {
    }

    /** Everything a register (grid) needs: who, which days, and what was recorded. */
    public record Register(School school, RoleName role, LocalDate from, LocalDate to, LocalDate today,
                           List<Person> people, Map<Long, Map<LocalDate, DailyAttendance>> records) {
    }

    private final DailyAttendanceRepository attendanceRepository;
    private final SchoolRepository schoolRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final InstructorProfileRepository instructorProfileRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final AdminSchoolScope adminSchoolScope;
    private final SchoolMapper schoolMapper;
    private final Clock clock;

    public AttendanceService(DailyAttendanceRepository attendanceRepository,
                             SchoolRepository schoolRepository,
                             StudentProfileRepository studentProfileRepository,
                             InstructorProfileRepository instructorProfileRepository,
                             UserRepository userRepository,
                             CurrentUserService currentUserService,
                             AdminSchoolScope adminSchoolScope,
                             SchoolMapper schoolMapper,
                             Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.schoolRepository = schoolRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.instructorProfileRepository = instructorProfileRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.adminSchoolScope = adminSchoolScope;
        this.schoolMapper = schoolMapper;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ school settings

    /** schoolId null = the calling admin's own school. */
    @Transactional
    public SchoolResponse updateSchoolLocation(Long schoolId, UpdateSchoolLocationRequest request) {
        Long targetId = schoolId != null ? schoolId : adminSchoolScope.restrictedSchoolId()
                .orElseThrow(() -> new BadRequestException("The bootstrap admin owns no school - use PUT /schools/{id}/location"));
        adminSchoolScope.requireAccess(targetId);
        School school = schoolRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("School", "id", targetId));
        if (request.getTimeZone() != null) {
            try {
                ZoneId.of(request.getTimeZone());
            } catch (DateTimeException e) {
                throw new BadRequestException("Unknown time zone: " + request.getTimeZone() + " (use e.g. Africa/Accra)");
            }
        }
        school.updateAttendanceSettings(request.getLatitude(), request.getLongitude(),
                request.getAttendanceRadiusMeters(), request.getTimeZone());
        return schoolMapper.toResponse(schoolRepository.save(school));
    }

    // ------------------------------------------------------------------ check-in

    @Transactional
    public DailyAttendanceResponse checkIn(CheckInRequest request) {
        Long userId = currentUserService.requireUserId();
        Membership me = membershipOf(userId);
        School school = me.school();
        boolean student = me.role() == RoleName.STUDENT;
        if (student && request.getLessonType() == null) {
            throw new BadRequestException("Choose whether today is a practical or a theory lesson");
        }

        Double distance = school.hasLocation()
                ? distanceMeters(request.getLatitude(), request.getLongitude(), school.getLatitude(), school.getLongitude())
                : null;
        // Why this check-in can't be counted as "at school" automatically, if it can't.
        ConfirmationReason unverified = !school.hasLocation() ? ConfirmationReason.SCHOOL_LOCATION_NOT_SET
                : request.getAccuracyMeters() > MAX_ACCURACY_METERS ? ConfirmationReason.LOCATION_NOT_PRECISE
                : distance > school.getAttendanceRadiusMeters() ? ConfirmationReason.OUTSIDE_SCHOOL_AREA
                : null;

        if (!student && unverified != null) {
            // Instructors still have to be at the school to check in.
            rejectInstructor(unverified, school, request, distance);
        }

        LocalDate today = today(school);
        if (attendanceRepository.findByUserIdAndAttendanceDate(userId, today).isPresent()) {
            throw new BadRequestException("You've already checked in today");
        }
        DailyAttendance record = DailyAttendance.builder()
                .school(school)
                .user(me.user())
                .role(me.role())
                .attendanceDate(today)
                // At school: present straight away. A student who can't be placed at the
                // school is still recorded, for an instructor (or the admin) to confirm.
                .status(unverified == null ? DailyAttendanceStatus.PRESENT : DailyAttendanceStatus.PENDING_CONFIRMATION)
                .confirmationReason(unverified)
                .source(AttendanceSource.CHECK_IN)
                .checkedInAt(LocalDateTime.now(clock))
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .accuracyMeters(round(request.getAccuracyMeters()))
                .distanceMeters(distance != null ? round(distance) : null)
                .lessonType(request.getLessonType())
                .topic(blankToNull(request.getTopic()))
                .build();
        return toResponse(attendanceRepository.save(record), me.name());
    }

    private void rejectInstructor(ConfirmationReason reason, School school, CheckInRequest request, Double distance) {
        switch (reason) {
            case SCHOOL_LOCATION_NOT_SET -> throw new BadRequestException(
                    "Your school hasn't set its attendance location yet - ask your school's admin");
            case LOCATION_NOT_PRECISE -> throw new DetailedBadRequestException(
                    "Your location isn't precise enough (about " + Math.round(request.getAccuracyMeters())
                            + " m). Turn on precise location or move outdoors and try again.",
                    Map.of("accuracyMeters", round(request.getAccuracyMeters()), "maxAccuracyMeters", MAX_ACCURACY_METERS));
            case OUTSIDE_SCHOOL_AREA -> throw new DetailedBadRequestException(
                    "You're about " + Math.round(distance) + " m from your school - you need to be within "
                            + school.getAttendanceRadiusMeters() + " m to check in.",
                    Map.of("distanceMeters", round(distance), "radiusMeters", school.getAttendanceRadiusMeters()));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public List<DailyAttendanceResponse> myHistory(LocalDate from, LocalDate to) {
        Long userId = currentUserService.requireUserId();
        Membership me = membershipOf(userId);
        LocalDate[] range = range(from, to, today(me.school()));
        return attendanceRepository.findForUser(userId, range[0], range[1]).stream()
                .map(record -> toResponse(record, me.name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DailyAttendanceResponse> userHistory(Long userId, LocalDate from, LocalDate to) {
        Membership target = membershipOf(userId);
        requireCanSee(target);
        LocalDate[] range = range(from, to, today(target.school()));
        return attendanceRepository.findForUser(userId, range[0], range[1]).stream()
                .map(record -> toResponse(record, target.name()))
                .toList();
    }

    /** One day at a school: everyone of the role, with a status even if they never checked in. */
    @Transactional(readOnly = true)
    public List<DailyAttendanceResponse> schoolDay(Long schoolId, LocalDate date, RoleName role) {
        return dayList(schoolId, date, role).rows();
    }

    /** A day list with its school, date and role - what the one-day export prints. */
    @Transactional(readOnly = true)
    public DayList dayList(Long schoolId, LocalDate date, RoleName role) {
        School school = staffSchool(schoolId);
        LocalDate day = date != null ? date : today(school);
        RoleName who = rosterRole(role);
        Map<Long, DailyAttendance> byUser = attendanceRepository.findForSchool(school.getId(), who, day, day).stream()
                .collect(Collectors.toMap(r -> r.getUser().getId(), Function.identity(), (a, b) -> a));
        LocalDate today = today(school);
        List<DailyAttendanceResponse> rows = roster(school.getId(), who).stream()
                .map(person -> {
                    DailyAttendance record = byUser.get(person.userId());
                    return record != null ? toResponse(record, person.name()) : missing(person, day, today);
                })
                .toList();
        return new DayList(school, day, who, rows);
    }

    /** The data behind a register export: people x days for a date range. */
    @Transactional(readOnly = true)
    public Register register(Long schoolId, LocalDate from, LocalDate to, RoleName role) {
        School school = staffSchool(schoolId);
        LocalDate today = today(school);
        LocalDate[] range = range(from, to, today);
        RoleName who = rosterRole(role);
        Map<Long, Map<LocalDate, DailyAttendance>> records = attendanceRepository
                .findForSchool(school.getId(), who, range[0], range[1]).stream()
                .collect(Collectors.groupingBy(r -> r.getUser().getId(),
                        Collectors.toMap(DailyAttendance::getAttendanceDate, Function.identity(), (a, b) -> a)));
        return new Register(school, who, range[0], range[1], today, roster(school.getId(), who), records);
    }

    // ------------------------------------------------------------------ staff actions

    @Transactional
    public DailyAttendanceResponse confirm(Long attendanceId) {
        DailyAttendance record = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance", "id", attendanceId));
        requireStaffOf(record.getSchool().getId());
        if (record.getStatus() != DailyAttendanceStatus.PENDING_CONFIRMATION) {
            throw new BadRequestException("Only a check-in awaiting confirmation can be confirmed");
        }
        record.confirm(currentUser(), LocalDateTime.now(clock));
        return toResponse(attendanceRepository.save(record), nameOf(record.getUser().getId()));
    }

    /** Records (or corrects) a day for someone - PRESENT, LATE or ABSENT. */
    @Transactional
    public DailyAttendanceResponse recordManually(ManualAttendanceRequest request) {
        if (!MANUAL_STATUSES.contains(request.getStatus())) {
            throw new BadRequestException("Status must be PRESENT, LATE or ABSENT");
        }
        Membership target = membershipOf(request.getUserId());
        if (currentUserService.hasRole(RoleName.ADMIN)) {
            adminSchoolScope.requireAccess(target.school().getId());
        } else if (!(target.role() == RoleName.STUDENT && callerIsInstructorOf(target.school().getId()))) {
            throw new ForbiddenException("Instructors can only record attendance for students of their own school");
        }
        if (request.getDate().isAfter(today(target.school()))) {
            throw new BadRequestException("Attendance can't be recorded for a future date");
        }

        User by = currentUser();
        DailyAttendance record = attendanceRepository.findByUserIdAndAttendanceDate(target.user().getId(), request.getDate())
                .map(existing -> {
                    existing.recordManually(request.getStatus(), request.getReason(), by,
                            request.getLessonType(), blankToNull(request.getTopic()));
                    return existing;
                })
                .orElseGet(() -> DailyAttendance.builder()
                        .school(target.school())
                        .user(target.user())
                        .role(target.role())
                        .attendanceDate(request.getDate())
                        .status(request.getStatus())
                        .source(AttendanceSource.MANUAL)
                        .recordedBy(by)
                        .reason(request.getReason())
                        .lessonType(request.getLessonType())
                        .topic(blankToNull(request.getTopic()))
                        .build());
        return toResponse(attendanceRepository.save(record), target.name());
    }

    // ------------------------------------------------------------------ helpers

    private record Membership(User user, School school, RoleName role, String name) {
    }

    private Membership membershipOf(Long userId) {
        return studentProfileRepository.findByUserId(userId)
                .map(p -> new Membership(p.getUser(), p.getSchool(), RoleName.STUDENT, fullName(p.getFirstName(), p.getLastName())))
                .or(() -> instructorProfileRepository.findByUserId(userId)
                        .map(p -> new Membership(p.getUser(), p.getSchool(), RoleName.INSTRUCTOR, fullName(p.getFirstName(), p.getLastName()))))
                .orElseThrow(() -> new ForbiddenException("Attendance is only kept for students and instructors"));
    }

    private String nameOf(Long userId) {
        return membershipOf(userId).name();
    }

    /** The school a staff member may read: admins within their scope, instructors their own. */
    private School staffSchool(Long schoolId) {
        requireStaffOf(schoolId);
        return schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School", "id", schoolId));
    }

    private void requireStaffOf(Long schoolId) {
        if (currentUserService.hasRole(RoleName.ADMIN)) {
            adminSchoolScope.requireAccess(schoolId);
            return;
        }
        if (!callerIsInstructorOf(schoolId)) {
            throw new ForbiddenException("You do not have access to this school's attendance");
        }
    }

    private boolean callerIsInstructorOf(Long schoolId) {
        return currentUserService.hasRole(RoleName.INSTRUCTOR)
                && instructorProfileRepository.findByUserId(currentUserService.requireUserId())
                .map(p -> p.getSchool().getId().equals(schoolId))
                .orElse(false);
    }

    /** Yourself; staff of your school (instructors: students only). */
    private void requireCanSee(Membership target) {
        if (target.user().getId().equals(currentUserService.requireUserId())) {
            return;
        }
        if (currentUserService.hasRole(RoleName.ADMIN)) {
            adminSchoolScope.requireAccess(target.school().getId());
            return;
        }
        if (target.role() == RoleName.STUDENT && callerIsInstructorOf(target.school().getId())) {
            return;
        }
        throw new ForbiddenException("You do not have access to this person's attendance");
    }

    private List<Person> roster(Long schoolId, RoleName role) {
        if (role == RoleName.INSTRUCTOR) {
            return instructorProfileRepository.findBySchoolIdExcludingDeletedUsers(schoolId).stream()
                    .filter(InstructorProfile::isActive)
                    .sorted(Comparator.comparing(InstructorProfile::getLastName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                            .thenComparing(InstructorProfile::getFirstName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .map(p -> new Person(p.getUser().getId(), fullName(p.getFirstName(), p.getLastName()), RoleName.INSTRUCTOR))
                    .toList();
        }
        return studentProfileRepository.findBySchoolIdExcludingDeletedUsers(schoolId).stream()
                .filter(p -> p.getStatus() == StudentStatus.ACTIVE)
                .sorted(Comparator.comparing(StudentProfile::getLastName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(StudentProfile::getFirstName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(p -> new Person(p.getUser().getId(), fullName(p.getFirstName(), p.getLastName()), RoleName.STUDENT))
                .toList();
    }

    private static RoleName rosterRole(RoleName role) {
        if (role == null) {
            return RoleName.STUDENT;
        }
        if (role != RoleName.STUDENT && role != RoleName.INSTRUCTOR) {
            throw new BadRequestException("role must be STUDENT or INSTRUCTOR");
        }
        return role;
    }

    private LocalDate[] range(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) {
            throw new BadRequestException("from must not be after to");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new BadRequestException("The date range can be at most " + MAX_RANGE_DAYS + " days");
        }
        return new LocalDate[]{start, end};
    }

    LocalDate today(School school) {
        return LocalDate.now(clock.withZone(ZoneId.of(school.getTimeZone())));
    }

    private DailyAttendanceResponse missing(Person person, LocalDate day, LocalDate today) {
        return DailyAttendanceResponse.builder()
                .userId(person.userId())
                .name(person.name())
                .role(person.role().name())
                .date(day)
                .status(day.isBefore(today) ? DailyAttendanceStatus.ABSENT : DailyAttendanceStatus.NOT_CHECKED_IN)
                .build();
    }

    private DailyAttendanceResponse toResponse(DailyAttendance record, String name) {
        return DailyAttendanceResponse.builder()
                .id(record.getId())
                .userId(record.getUser().getId())
                .name(name)
                .role(record.getRole().name())
                .date(record.getAttendanceDate())
                .status(record.getStatus())
                .source(record.getSource())
                .checkedInAt(record.getCheckedInAt())
                .distanceMeters(record.getDistanceMeters())
                .accuracyMeters(record.getAccuracyMeters())
                .confirmedAt(record.getConfirmedAt())
                .confirmedByName(record.getConfirmedBy() != null ? displayName(record.getConfirmedBy()) : null)
                .recordedByName(record.getRecordedBy() != null ? displayName(record.getRecordedBy()) : null)
                .reason(record.getReason())
                .lessonType(record.getLessonType())
                .topic(record.getTopic())
                .confirmationReason(record.getConfirmationReason())
                .build();
    }

    private static String displayName(User user) {
        return user.getDisplayName();
    }

    private User currentUser() {
        Long userId = currentUserService.requireUserId();
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private static String fullName(String first, String last) {
        return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
    }

    private static double round(double meters) {
        return Math.round(meters * 10) / 10.0;
    }

    /** Great-circle distance (haversine) - plenty accurate at school-yard scale. */
    static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }
}
