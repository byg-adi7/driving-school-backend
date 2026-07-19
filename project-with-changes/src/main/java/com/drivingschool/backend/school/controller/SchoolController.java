package com.drivingschool.backend.school.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.common.util.SecurityUtils;
import com.drivingschool.backend.school.dto.CreateSchoolWithAdminRequest;
import com.drivingschool.backend.school.dto.SchoolDeletionRequestResponse;
import com.drivingschool.backend.school.dto.SchoolResponse;
import com.drivingschool.backend.school.dto.SchoolWithAdminResponse;
import com.drivingschool.backend.school.service.SchoolDeletionRequestService;
import com.drivingschool.backend.school.service.SchoolService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schools")
@Tag(name = "Schools", description = "Driving school management")
@SecurityRequirement(name = "Bearer Authentication")
public class SchoolController {

    private final SchoolService schoolService;
    private final SchoolDeletionRequestService schoolDeletionRequestService;

    public SchoolController(SchoolService schoolService, SchoolDeletionRequestService schoolDeletionRequestService) {
        this.schoolService = schoolService;
        this.schoolDeletionRequestService = schoolDeletionRequestService;
    }

    @PostMapping
    @Operation(summary = "Bootstrap admin: create a new driving school and its owning admin account")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SchoolWithAdminResponse>> create(
            @Valid @RequestBody CreateSchoolWithAdminRequest request) {
        SchoolWithAdminResponse response = schoolService.createWithAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("School and owning admin created successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get school by ID",
            description = "Bootstrap admin can view any school; a non-bootstrap admin may only view their own.")
    public ResponseEntity<ApiResponse<SchoolResponse>> getById(@PathVariable Long id) {
        Long callerId = SecurityUtils.getCurrentUserId();
        String callerRole = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(schoolService.getById(id, callerId, callerRole)));
    }

    @GetMapping
    @Operation(summary = "List active schools",
            description = "Bootstrap admin sees all schools; a non-bootstrap admin sees only their own; " +
                    "instructors/students see the full list (registration picker).")
    public ResponseEntity<ApiResponse<List<SchoolResponse>>> getAllActive() {
        Long callerId = SecurityUtils.getCurrentUserId();
        String callerRole = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(schoolService.getAllActive(callerId, callerRole)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Bootstrap admin: permanently delete a school and its owning admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        schoolService.deleteDirectly(id);
        return ResponseEntity.ok(ApiResponse.success("School and its owning admin deleted successfully", null));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Admin: request deletion of your own school",
            description = "Only a request - requires bootstrap admin approval before the school and your " +
                    "account are actually deleted.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SchoolDeletionRequestResponse>> requestOwnDeletion() {
        Long callerId = SecurityUtils.getCurrentUserId();
        SchoolDeletionRequestResponse response = schoolDeletionRequestService.requestOwnSchoolDeletion(callerId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Deletion request submitted for bootstrap admin approval", response));
    }
}
