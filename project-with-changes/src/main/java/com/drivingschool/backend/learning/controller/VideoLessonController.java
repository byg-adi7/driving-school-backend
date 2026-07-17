package com.drivingschool.backend.learning.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.common.util.SecurityUtils;
import com.drivingschool.backend.learning.dto.CreateVideoLessonRequest;
import com.drivingschool.backend.learning.dto.UpdateVideoLessonRequest;
import com.drivingschool.backend.learning.dto.VideoLessonResponse;
import com.drivingschool.backend.learning.service.VideoLessonService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/video-lessons")
@Tag(name = "Video Lessons", description = "Course video lesson authoring and browsing")
@SecurityRequirement(name = "Bearer Authentication")
public class VideoLessonController {

    private final VideoLessonService videoLessonService;

    public VideoLessonController(VideoLessonService videoLessonService) {
        this.videoLessonService = videoLessonService;
    }

    @PostMapping
    @Operation(summary = "Add a video lesson to a course")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<VideoLessonResponse>> create(@Valid @RequestBody CreateVideoLessonRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Video lesson created", videoLessonService.create(request, userId, role)));
    }

    @PutMapping("/{lessonId}")
    @Operation(summary = "Update a video lesson")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<VideoLessonResponse>> update(
            @PathVariable Long lessonId, @Valid @RequestBody UpdateVideoLessonRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(videoLessonService.update(lessonId, request, userId, role)));
    }

    @PutMapping("/{lessonId}/publish")
    @Operation(summary = "Publish a video lesson")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<VideoLessonResponse>> publish(@PathVariable Long lessonId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(videoLessonService.publish(lessonId, userId, role)));
    }

    @PutMapping("/{lessonId}/unpublish")
    @Operation(summary = "Unpublish a video lesson back to draft")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<VideoLessonResponse>> unpublish(@PathVariable Long lessonId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(videoLessonService.unpublish(lessonId, userId, role)));
    }

    @GetMapping("/{lessonId}")
    @Operation(summary = "Get a video lesson by ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<VideoLessonResponse>> getById(@PathVariable Long lessonId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(videoLessonService.getById(lessonId, userId, role)));
    }

    @GetMapping("/course/{courseId}")
    @Operation(summary = "List video lessons for a course (published only, unless owner/admin)")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<VideoLessonResponse>>> getByCourse(@PathVariable Long courseId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success(videoLessonService.getByCourse(courseId, userId, role)));
    }
}
