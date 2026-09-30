package com.drivingschool.backend.lesson.route.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.common.util.SecurityUtils;
import com.drivingschool.backend.lesson.route.dto.GenerateRouteRequest;
import com.drivingschool.backend.lesson.route.dto.RouteResponse;
import com.drivingschool.backend.lesson.route.service.PracticalLessonRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lesson-routes")
@Tag(name = "Lesson Routes", description = "Manage practical lesson route planning")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
public class PracticalLessonRouteController {

    private final PracticalLessonRouteService routeService;

    @PostMapping("/generate")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Operation(summary = "Generate lesson route", description = "Instructors can generate routes for practical lessons")
    public ResponseEntity<ApiResponse<RouteResponse>> generateRoute(
            @Valid @RequestBody GenerateRouteRequest request
    ) {
        Long callerId = SecurityUtils.getCurrentUserId();
        RouteResponse response = routeService.generateRoute(request, callerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success( "Route generated successfully",response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get route details", description = "Get details of a specific lesson route")
    public ResponseEntity<ApiResponse<RouteResponse>> getRoute(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        RouteResponse response = routeService.getRoute(id, userId, role);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get route by booking", description = "Get route for a specific booking")
    public ResponseEntity<ApiResponse<RouteResponse>> getRouteByBooking(
            @PathVariable Long bookingId
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        RouteResponse response = routeService.getRouteByBooking(bookingId, userId, role);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My lesson routes", description = "A student's routes: the ones planned for their own lessons, newest first")
    public ResponseEntity<ApiResponse<Page<RouteResponse>>> getMyRoutes(Pageable pageable) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(routeService.getMyRoutesAsStudent(userId, pageable)));
    }

    @GetMapping("/instructor/{instructorId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Get instructor routes", description = "Get all routes created by an instructor")
    public ResponseEntity<ApiResponse<Page<RouteResponse>>> getInstructorRoutes(
            @PathVariable Long instructorId,
            Pageable pageable
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        Page<RouteResponse> routes = routeService.getInstructorRoutes(instructorId, pageable, userId, role);
        return ResponseEntity.ok(ApiResponse.success(routes));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all routes", description = "Admin can retrieve all lesson routes")
    public ResponseEntity<ApiResponse<Page<RouteResponse>>> getAllRoutes(Pageable pageable) {
        Page<RouteResponse> routes = routeService.getAllRoutes(pageable);
        return ResponseEntity.ok(ApiResponse.success(routes));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Operation(summary = "Delete route", description = "Instructors can delete their own routes")
    public ResponseEntity<ApiResponse<Void>> deleteRoute(@PathVariable Long id) {
        Long callerId = SecurityUtils.getCurrentUserId();
        routeService.deleteRoute(id, callerId);
        return ResponseEntity.ok(ApiResponse.success( "Route deleted successfully",null));
    }
}
