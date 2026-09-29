package com.drivingschool.backend.messaging.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.messaging.dto.AnnouncementResponse;
import com.drivingschool.backend.messaging.dto.CreateAnnouncementRequest;
import com.drivingschool.backend.messaging.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/announcements")
@Tag(name = "Announcements", description = "One-way messages from an instructor to every student of their school")
@SecurityRequirement(name = "Bearer Authentication")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @PostMapping
    @Operation(summary = "Send an announcement to every student of my school (in-app + email)")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> create(@Valid @RequestBody CreateAnnouncementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Announcement sent", announcementService.create(request)));
    }

    @GetMapping
    @Operation(summary = "My school's announcements, newest first (bootstrap admin: every school's)")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<AnnouncementResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(announcementService.listForMySchool(pageable)));
    }
}
