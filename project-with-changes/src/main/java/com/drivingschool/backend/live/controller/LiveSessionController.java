package com.drivingschool.backend.live.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.common.util.SecurityUtils;
import com.drivingschool.backend.live.dto.AttendanceResponse;
import com.drivingschool.backend.live.dto.CreateLiveSessionRequest;
import com.drivingschool.backend.live.dto.LiveSessionResponse;
import com.drivingschool.backend.live.dto.RegisterAttendanceRequest;
import com.drivingschool.backend.live.enums.SessionStatus;
import com.drivingschool.backend.live.service.LiveSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/live-sessions")
@Tag(name = "Live Sessions", description = "Live class scheduling and attendance")
@SecurityRequirement(name = "Bearer Authentication")
public class LiveSessionController {

    private final LiveSessionService liveSessionService;

    public LiveSessionController(LiveSessionService liveSessionService) {
        this.liveSessionService = liveSessionService;
    }

    @PostMapping
    @Operation(summary = "Schedule a live session")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<LiveSessionResponse>> schedule(
            @Valid @RequestBody CreateLiveSessionRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Session scheduled", liveSessionService.schedule(request, userId, role)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get live session by ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<LiveSessionResponse>> getById(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(liveSessionService.getById(id, userId, role)));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update session status")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<LiveSessionResponse>> updateStatus(
            @PathVariable Long id,
            @RequestParam SessionStatus status) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(liveSessionService.updateStatus(id, status, userId, role)));
    }

    @GetMapping("/school/{schoolId}/upcoming")
    @Operation(summary = "List upcoming sessions for a school (next 7 days)")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<LiveSessionResponse>>> getUpcoming(@PathVariable Long schoolId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(liveSessionService.getUpcomingBySchool(schoolId, userId, role)));
    }

    @PostMapping("/{id}/register")
    @Operation(summary = "Register a student for a live session")
    @PreAuthorize("hasAnyRole('ADMIN', 'STUDENT')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> register(
            @PathVariable Long id,
            @Valid @RequestBody RegisterAttendanceRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registered", liveSessionService.register(id, request, userId, role)));
    }

    @DeleteMapping("/{id}/register")
    @Operation(summary = "Unregister yourself from a live session (before you're marked present and before it ends)")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<Void>> unregister(@PathVariable Long id) {
        liveSessionService.unregister(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Unregistered", null));
    }

    @PutMapping("/{id}/attendance/{studentId}/present")
    @Operation(summary = "Mark student as present")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<AttendanceResponse>> markPresent(
            @PathVariable Long id,
            @PathVariable Long studentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(liveSessionService.markPresent(id, studentId, userId, role)));
    }

    @GetMapping("/{id}/attendance")
    @Operation(summary = "List attendance for a session")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getAttendance(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(liveSessionService.getAttendance(id, userId, role)));
    }
}
