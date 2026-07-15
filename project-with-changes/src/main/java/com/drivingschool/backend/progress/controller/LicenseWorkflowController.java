package com.drivingschool.backend.progress.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.common.util.SecurityUtils;
import com.drivingschool.backend.progress.dto.AdvanceStageRequest;
import com.drivingschool.backend.progress.dto.LicenseWorkflowResponse;
import com.drivingschool.backend.progress.service.LicenseWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/v1/progress/license")
@Tag(name = "License Workflow", description = "Student license progression management")
@SecurityRequirement(name = "Bearer Authentication")
public class LicenseWorkflowController {

    private final LicenseWorkflowService licenseWorkflowService;

    public LicenseWorkflowController(LicenseWorkflowService licenseWorkflowService) {
        this.licenseWorkflowService = licenseWorkflowService;
    }

    @GetMapping("/students/{studentId}")
    @Operation(summary = "Get license workflow for a student")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<LicenseWorkflowResponse>> getByStudentId(
            @PathVariable Long studentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(
                licenseWorkflowService.getByStudentId(studentId, userId, role)));
    }

    @PostMapping("/students/{studentId}/initialize")
    @Operation(summary = "Initialize license workflow for a student")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LicenseWorkflowResponse>> initialize(
            @PathVariable Long studentId) {
        LicenseWorkflowResponse response = licenseWorkflowService.initializeForStudent(studentId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("License workflow initialized", response));
    }

    @PutMapping("/students/{studentId}/theory-progress")
    @Operation(summary = "Update theory learning progress percentage")
    @PreAuthorize("hasAnyRole('ADMIN', 'STUDENT')")
    public ResponseEntity<ApiResponse<LicenseWorkflowResponse>> updateTheoryProgress(
            @PathVariable Long studentId,
            @RequestParam int progressPercent) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(
                licenseWorkflowService.updateTheoryProgress(studentId, progressPercent, userId, role)));
    }

    @PostMapping("/students/{studentId}/quiz-passed")
    @Operation(summary = "Mark quiz as passed for a student",
            description = "Admin-only. The normal path is QuizService.submit() calling this internally " +
                    "after verifying an actual passing quiz submission - it must not be directly callable " +
                    "by students, who could otherwise skip taking the quiz entirely.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LicenseWorkflowResponse>> markQuizPassed(
            @PathVariable Long studentId) {
        return ResponseEntity.ok(ApiResponse.success(
                licenseWorkflowService.markQuizPassed(studentId)));
    }

    @PutMapping("/students/{studentId}/advance")
    @Operation(summary = "Advance student to next license stage")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<LicenseWorkflowResponse>> advanceStage(
            @PathVariable Long studentId,
            @Valid @RequestBody AdvanceStageRequest request) {
        Long instructorUserId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                licenseWorkflowService.advanceStage(studentId, request, instructorUserId)));
    }
}
