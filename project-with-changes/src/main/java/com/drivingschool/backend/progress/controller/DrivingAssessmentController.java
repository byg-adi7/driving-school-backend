package com.drivingschool.backend.progress.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.common.util.SecurityUtils;
import com.drivingschool.backend.progress.dto.CreateDrivingAssessmentRequest;
import com.drivingschool.backend.progress.dto.DrivingAssessmentResponse;
import com.drivingschool.backend.progress.dto.UpdateDrivingAssessmentFeedbackRequest;
import com.drivingschool.backend.progress.service.DrivingAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/driving-assessments")
@Tag(name = "Driving Assessments", description = "Instructor-recorded practical driving assessments")
@SecurityRequirement(name = "Bearer Authentication")
public class DrivingAssessmentController {

    private final DrivingAssessmentService drivingAssessmentService;

    public DrivingAssessmentController(DrivingAssessmentService drivingAssessmentService) {
        this.drivingAssessmentService = drivingAssessmentService;
    }

    @PostMapping
    @Operation(summary = "Record a driving assessment", description = "Instructors record a pass/fail assessment for one of their students")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<DrivingAssessmentResponse>> createAssessment(
            @Valid @RequestBody CreateDrivingAssessmentRequest request) {
        Long callerId = SecurityUtils.getCurrentUserId();
        DrivingAssessmentResponse response = drivingAssessmentService.createAssessment(request, callerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Driving assessment recorded successfully", response));
    }

    @PatchMapping("/{id}/feedback")
    @Operation(summary = "Update assessment feedback", description = "Only the authoring instructor may edit feedback")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<DrivingAssessmentResponse>> updateFeedback(
            @PathVariable Long id, @Valid @RequestBody UpdateDrivingAssessmentFeedbackRequest request) {
        Long callerId = SecurityUtils.getCurrentUserId();
        DrivingAssessmentResponse response = drivingAssessmentService.updateFeedback(id, request, callerId);
        return ResponseEntity.ok(ApiResponse.success("Feedback updated successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a driving assessment")
    public ResponseEntity<ApiResponse<DrivingAssessmentResponse>> getAssessment(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(drivingAssessmentService.getAssessment(id, userId, role)));
    }

    @GetMapping("/student/{studentId}")
    @Operation(summary = "Get a student's driving assessments")
    public ResponseEntity<ApiResponse<List<DrivingAssessmentResponse>>> getStudentAssessments(@PathVariable Long studentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(
                drivingAssessmentService.getStudentAssessments(studentId, userId, role)));
    }

    @GetMapping("/instructor/{instructorId}")
    @Operation(summary = "Get an instructor's driving assessments")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<DrivingAssessmentResponse>>> getInstructorAssessments(@PathVariable Long instructorId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(
                drivingAssessmentService.getInstructorAssessments(instructorId, userId, role)));
    }
}
