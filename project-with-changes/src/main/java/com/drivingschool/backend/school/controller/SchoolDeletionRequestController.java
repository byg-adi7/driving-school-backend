package com.drivingschool.backend.school.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.common.util.SecurityUtils;
import com.drivingschool.backend.school.dto.ReviewSchoolDeletionRequest;
import com.drivingschool.backend.school.dto.SchoolDeletionRequestResponse;
import com.drivingschool.backend.school.service.SchoolDeletionRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/school-deletion-requests")
@Tag(name = "School Deletion Requests", description = "Bootstrap-admin review queue for school+admin deletion requests")
@SecurityRequirement(name = "Bearer Authentication")
public class SchoolDeletionRequestController {

    private final SchoolDeletionRequestService schoolDeletionRequestService;

    public SchoolDeletionRequestController(SchoolDeletionRequestService schoolDeletionRequestService) {
        this.schoolDeletionRequestService = schoolDeletionRequestService;
    }

    @GetMapping
    @Operation(summary = "Bootstrap admin: list pending school deletion requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<SchoolDeletionRequestResponse>>> listPending(Pageable pageable) {
        Long callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(schoolDeletionRequestService.listPending(pageable, callerId)));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Bootstrap admin: approve a school deletion request",
            description = "Permanently deletes the school and the requesting admin's account.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SchoolDeletionRequestResponse>> approve(
            @PathVariable Long id, @RequestBody(required = false) ReviewSchoolDeletionRequest body) {
        Long callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Request approved; school and admin account deleted",
                schoolDeletionRequestService.approve(id, callerId, body)));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Bootstrap admin: reject a school deletion request")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SchoolDeletionRequestResponse>> reject(
            @PathVariable Long id, @RequestBody(required = false) ReviewSchoolDeletionRequest body) {
        Long callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Request rejected",
                schoolDeletionRequestService.reject(id, callerId, body)));
    }
}
