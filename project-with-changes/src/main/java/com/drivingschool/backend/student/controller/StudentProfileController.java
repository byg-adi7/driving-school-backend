package com.drivingschool.backend.student.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.student.dto.StudentProfileResponse;
import com.drivingschool.backend.student.dto.UpdateStudentProfileRequest;
import com.drivingschool.backend.student.dto.UpdateStudentStatusRequest;
import com.drivingschool.backend.student.service.StudentProfileService;
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
@RequestMapping("/api/v1/students")
@Tag(name = "Student Profiles", description = "Student profile management")
@SecurityRequirement(name = "Bearer Authentication")
public class StudentProfileController {

    private final StudentProfileService studentProfileService;

    public StudentProfileController(StudentProfileService studentProfileService) {
        this.studentProfileService = studentProfileService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current student's own profile")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.success(studentProfileService.getMyProfile()));
    }

    @PutMapping("/me")
    @Operation(summary = "Update the current student's own profile")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> updateMyProfile(
            @Valid @RequestBody UpdateStudentProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully",
                studentProfileService.updateMyProfile(request)));
    }

    @GetMapping("/school/{schoolId}")
    @Operation(summary = "List students belonging to a school",
            description = "Admin can list any school; an instructor may only list their own school.")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<StudentProfileResponse>>> getBySchool(@PathVariable Long schoolId) {
        return ResponseEntity.ok(ApiResponse.success(studentProfileService.getBySchool(schoolId)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Admin: update a student's status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateStudentStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Student status updated successfully",
                studentProfileService.updateStatus(id, request)));
    }
}
