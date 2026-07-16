package com.drivingschool.backend.instructor.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.instructor.dto.InstructorProfileResponse;
import com.drivingschool.backend.instructor.dto.UpdateInstructorActiveStatusRequest;
import com.drivingschool.backend.instructor.dto.UpdateInstructorProfileRequest;
import com.drivingschool.backend.instructor.service.InstructorProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/instructors")
@Tag(name = "Instructor Profiles", description = "Instructor profile management")
@SecurityRequirement(name = "Bearer Authentication")
public class InstructorProfileController {

    private final InstructorProfileService instructorProfileService;

    public InstructorProfileController(InstructorProfileService instructorProfileService) {
        this.instructorProfileService = instructorProfileService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current instructor's own profile")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<InstructorProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.success(instructorProfileService.getMyProfile()));
    }

    @PutMapping("/me")
    @Operation(summary = "Update the current instructor's own profile")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<InstructorProfileResponse>> updateMyProfile(
            @Valid @RequestBody UpdateInstructorProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully",
                instructorProfileService.updateMyProfile(request)));
    }

    @GetMapping("/school/{schoolId}")
    @Operation(summary = "Admin: list instructors belonging to a school")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<InstructorProfileResponse>>> getBySchool(@PathVariable Long schoolId) {
        return ResponseEntity.ok(ApiResponse.success(instructorProfileService.getBySchool(schoolId)));
    }

    @PatchMapping("/{id}/active")
    @Operation(summary = "Admin: activate or deactivate an instructor")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<InstructorProfileResponse>> updateActiveStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateInstructorActiveStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Instructor status updated successfully",
                instructorProfileService.updateActiveStatus(id, request)));
    }
}
