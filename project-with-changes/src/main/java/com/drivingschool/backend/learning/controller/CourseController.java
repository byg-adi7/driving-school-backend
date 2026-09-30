package com.drivingschool.backend.learning.controller;

import com.drivingschool.backend.common.exception.ForbiddenException;
import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.common.util.SecurityUtils;
import com.drivingschool.backend.learning.dto.CourseResponse;
import com.drivingschool.backend.learning.dto.CreateCourseRequest;
import com.drivingschool.backend.learning.dto.UpdateCourseRequest;
import com.drivingschool.backend.learning.service.CourseService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/courses")
@Tag(name = "Courses", description = "Course authoring and browsing")
@SecurityRequirement(name = "Bearer Authentication")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    @Operation(summary = "Create a course")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseResponse>> create(@Valid @RequestBody CreateCourseRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course created", courseService.create(request, userId, role)));
    }

    @PutMapping("/{courseId}")
    @Operation(summary = "Update a course's title/description")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseResponse>> update(
            @PathVariable Long courseId, @Valid @RequestBody UpdateCourseRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(courseService.update(courseId, request, userId, role)));
    }

    @PutMapping("/{courseId}/publish")
    @Operation(summary = "Publish a course")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseResponse>> publish(@PathVariable Long courseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success("Course published", courseService.publish(courseId, userId, role)));
    }

    @PutMapping("/{courseId}/unpublish")
    @Operation(summary = "Unpublish a course back to draft")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseResponse>> unpublish(@PathVariable Long courseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(courseService.unpublish(courseId, userId, role)));
    }

    @PutMapping("/{courseId}/archive")
    @Operation(summary = "Archive a course")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseResponse>> archive(@PathVariable Long courseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(courseService.archive(courseId, userId, role)));
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "Get a course by ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<CourseResponse>> getById(@PathVariable Long courseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(courseService.getById(courseId, userId, role)));
    }

    @GetMapping
    @Operation(summary = "List published courses (admins: ?includeDrafts=true for every course of their school)")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> getPublished(
            @RequestParam(defaultValue = "false") boolean includeDrafts) {
        if (includeDrafts) {
            // Instructors have GET /courses/mine for their own drafts; students never see drafts.
            if (!"ADMIN".equals(SecurityUtils.getCurrentUserRole())) {
                throw new ForbiddenException("Only admins can list draft courses - instructors use GET /courses/mine");
            }
            return ResponseEntity.ok(ApiResponse.success(courseService.getAllIncludingDrafts()));
        }
        return ResponseEntity.ok(ApiResponse.success(courseService.getPublished()));
    }

    @GetMapping("/mine")
    @Operation(summary = "List the current instructor's own courses, including drafts")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> getMine() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(courseService.getMine(userId)));
    }
}
