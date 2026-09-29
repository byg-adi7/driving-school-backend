package com.drivingschool.backend.learning.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.common.util.SecurityUtils;
import com.drivingschool.backend.learning.dto.CreateResourceRequest;
import com.drivingschool.backend.learning.dto.ResourceResponse;
import com.drivingschool.backend.learning.dto.UpdateResourceRequest;
import com.drivingschool.backend.learning.service.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
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

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PDF as a resource of a video lesson",
            description = "Multipart form: lessonId, title, file (PDF, max 50 MB). Stored like lesson-note attachments.")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ResourceResponse>> upload(@RequestParam Long lessonId,
                                                                @RequestParam String title,
                                                                @RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Resource uploaded", resourceService.upload(lessonId, title, file, userId, role)));
    }

    @PutMapping("/{resourceId}")
    @Operation(summary = "Rename a resource")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ResourceResponse>> rename(@PathVariable Long resourceId,
                                                                @Valid @RequestBody UpdateResourceRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success("Resource updated", resourceService.rename(resourceId, request, userId, role)));
    }

    @PutMapping(value = "/{resourceId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Replace an uploaded resource's file", description = "Multipart form: file (PDF)")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ResourceResponse>> replaceFile(@PathVariable Long resourceId,
                                                                     @RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        return ResponseEntity.ok(ApiResponse.success("Resource file replaced", resourceService.replaceFile(resourceId, file, userId, role)));
    }

    @GetMapping("/{resourceId}/download")
    @Operation(summary = "Download an uploaded resource's file",
            description = "inline=true serves it for viewing in the browser's PDF viewer instead of saving it")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long resourceId,
                                                                         @RequestParam(defaultValue = "false") boolean inline) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        ResourceService.DownloadableFile file = resourceService.download(resourceId, userId, role);
        String fileName = file.fileName() != null ? file.fileName() : "resource.pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(file.content());
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
