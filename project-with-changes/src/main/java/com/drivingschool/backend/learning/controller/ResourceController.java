package com.drivingschool.backend.learning.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.common.util.SecurityUtils;
import com.drivingschool.backend.learning.dto.CreateResourceRequest;
import com.drivingschool.backend.learning.dto.ResourceResponse;
import com.drivingschool.backend.learning.service.ResourceService;
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
@RequestMapping("/api/v1/resources")
@Tag(name = "Learning Resources", description = "Supplementary resources attached to a video lesson")
@SecurityRequirement(name = "Bearer Authentication")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping
    @Operation(summary = "Attach a resource to a video lesson")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ResourceResponse>> create(@Valid @RequestBody CreateResourceRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Resource added", resourceService.create(request, userId, role)));
    }

    @DeleteMapping("/{resourceId}")
    @Operation(summary = "Delete a resource")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long resourceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        resourceService.delete(resourceId, userId, role);
        return ResponseEntity.ok(ApiResponse.success("Resource deleted", null));
    }

    @GetMapping("/lesson/{lessonId}")
    @Operation(summary = "List resources for a video lesson")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<ResourceResponse>>> getByLesson(@PathVariable Long lessonId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(resourceService.getByLesson(lessonId, userId, role)));
    }
}
