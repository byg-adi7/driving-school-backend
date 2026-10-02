package com.drivingschool.backend.user.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.user.service.ProfilePhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Profile photos")
@SecurityRequirement(name = "Bearer Authentication")
public class ProfilePhotoController {

    private final ProfilePhotoService photoService;

    public ProfilePhotoController(ProfilePhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace my profile photo (JPEG/PNG/WebP, max 5 MB)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProfilePhotoService.PhotoResponse>> uploadMine(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Photo saved", photoService.upload(null, file)));
    }

    @DeleteMapping("/me/photo")
    @Operation(summary = "Remove my profile photo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProfilePhotoService.PhotoResponse>> removeMine() {
        return ResponseEntity.ok(ApiResponse.success("Photo removed", photoService.remove(null)));
    }

    @PostMapping(value = "/{userId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload someone's profile photo: admin for their school's accounts, instructor for their students")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ProfilePhotoService.PhotoResponse>> upload(@PathVariable Long userId,
                                                                                 @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Photo saved", photoService.upload(userId, file)));
    }

    @DeleteMapping("/{userId}/photo")
    @Operation(summary = "Remove someone's profile photo (same rules as uploading it)")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ProfilePhotoService.PhotoResponse>> remove(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Photo removed", photoService.remove(userId)));
    }
}
