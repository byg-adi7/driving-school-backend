package com.drivingschool.backend.attendance.controller;

import com.drivingschool.backend.attendance.dto.CheckInRequest;
import com.drivingschool.backend.attendance.dto.DailyAttendanceResponse;
import com.drivingschool.backend.attendance.dto.ManualAttendanceRequest;
import com.drivingschool.backend.attendance.dto.RollCallRequest;
import com.drivingschool.backend.attendance.service.AttendanceExportService;
import com.drivingschool.backend.attendance.service.AttendanceService;
import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.role.enums.RoleName;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/attendance")
@Tag(name = "Attendance", description = "Daily location check-in and attendance registers")
@SecurityRequirement(name = "Bearer Authentication")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceExportService exportService;

    public AttendanceController(AttendanceService attendanceService, AttendanceExportService exportService) {
        this.attendanceService = attendanceService;
        this.exportService = exportService;
    }

    @PostMapping("/check-in")
    @Operation(summary = "Check in for today from your current location")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<DailyAttendanceResponse>> checkIn(@Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Checked in", attendanceService.checkIn(request)));
    }

    @GetMapping("/me")
    @Operation(summary = "My attendance history (default: the last 30 days)")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<DailyAttendanceResponse>>> me(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.myHistory(from, to)));
    }

    @GetMapping("/school/{schoolId}")
    @Operation(summary = "One day's attendance for a school, including people who haven't checked in")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<DailyAttendanceResponse>>> schoolDay(
            @PathVariable Long schoolId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) RoleName role) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.schoolDay(schoolId, date, role)));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "One person's attendance history")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<DailyAttendanceResponse>>> userHistory(
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(attendanceService.userHistory(userId, from, to)));
    }

    @PutMapping("/{attendanceId}/confirm")
    @Operation(summary = "Confirm a student's check-in (awaiting confirmation -> present)")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<DailyAttendanceResponse>> confirm(@PathVariable Long attendanceId) {
        return ResponseEntity.ok(ApiResponse.success("Attendance confirmed", attendanceService.confirm(attendanceId)));
    }

    @PostMapping("/manual")
    @Operation(summary = "Record or correct someone's attendance for a day (PRESENT, LATE or ABSENT)")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<DailyAttendanceResponse>> manual(@Valid @RequestBody ManualAttendanceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Attendance recorded", attendanceService.recordManually(request)));
    }

    @PutMapping("/school/{schoolId}/roll-call")
    @Operation(summary = "Save a day's roll call: who was really there (PRESENT/LATE) and who wasn't (ABSENT)")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<DailyAttendanceResponse>>> rollCall(
            @PathVariable Long schoolId, @Valid @RequestBody RollCallRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Roll call saved", attendanceService.rollCall(schoolId, request)));
    }

    @GetMapping("/school/{schoolId}/export/register")
    @Operation(summary = "Download a printable Excel register: people x days for a date range (max 62 days)")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<byte[]> exportRegister(
            @PathVariable Long schoolId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) RoleName role) {
        AttendanceService.Register register = attendanceService.register(schoolId, from, to, role);
        String name = "attendance-register-" + register.role().name().toLowerCase() + "-"
                + register.from() + "-to-" + register.to() + ".xlsx";
        return xlsx(exportService.register(register), name);
    }

    @GetMapping("/school/{schoolId}/export/day")
    @Operation(summary = "Download a printable Excel list of one day's attendance (default: today)")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<byte[]> exportDay(
            @PathVariable Long schoolId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) RoleName role) {
        AttendanceService.DayList day = attendanceService.dayList(schoolId, date, role);
        String name = "attendance-" + day.role().name().toLowerCase() + "-" + day.date() + ".xlsx";
        return xlsx(exportService.day(day.school(), day.date(), day.role(), day.rows()), name);
    }

    private static ResponseEntity<byte[]> xlsx(byte[] content, String fileName) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(AttendanceExportService.XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(content);
    }
}
