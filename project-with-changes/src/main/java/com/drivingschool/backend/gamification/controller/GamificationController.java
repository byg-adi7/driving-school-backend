package com.drivingschool.backend.gamification.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.common.util.SecurityUtils;
import com.drivingschool.backend.gamification.dto.GamificationSummaryResponse;
import com.drivingschool.backend.gamification.dto.LeaderboardEntryResponse;
import com.drivingschool.backend.gamification.service.GamificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gamification")
@Tag(name = "Gamification", description = "Student points, streaks, badges, and school leaderboard")
@SecurityRequirement(name = "Bearer Authentication")
public class GamificationController {

    private final GamificationService gamificationService;

    public GamificationController(GamificationService gamificationService) {
        this.gamificationService = gamificationService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get my gamification summary")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<GamificationSummaryResponse>> getMySummary() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(gamificationService.getMySummary(userId)));
    }

    @GetMapping("/students/{studentId}")
    @Operation(summary = "Get a student's gamification summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<GamificationSummaryResponse>> getStudentSummary(@PathVariable Long studentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(
                gamificationService.getStudentSummary(studentId, userId, role)));
    }

    @GetMapping("/leaderboard/school/{schoolId}")
    @Operation(summary = "Get the school-wide leaderboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<Page<LeaderboardEntryResponse>>> getSchoolLeaderboard(
            @PathVariable Long schoolId, Pageable pageable) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(
                gamificationService.getSchoolLeaderboard(schoolId, pageable, userId, role)));
    }
}
