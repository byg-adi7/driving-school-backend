package com.drivingschool.backend.lesson.note.controller;

import com.drivingschool.backend.common.exception.ServiceUnavailableException;
import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.common.util.SecurityUtils;
import com.drivingschool.backend.lesson.note.dto.AttachmentResponse;
import com.drivingschool.backend.lesson.note.dto.CreateLessonNoteRequest;
import com.drivingschool.backend.lesson.note.dto.LessonNoteResponse;
import com.drivingschool.backend.lesson.note.dto.UpdateLessonNoteRequest;
import com.drivingschool.backend.lesson.note.service.LessonNoteAttachmentService;
import com.drivingschool.backend.lesson.note.service.LessonNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/lesson-notes")
@Tag(name = "Lesson Notes", description = "Manage lesson notes for practical lessons")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
public class LessonNoteController {

    private final LessonNoteService lessonNoteService;
    private final LessonNoteAttachmentService attachmentService;


    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Operation(summary = "Create lesson note", description = "Instructors can create notes for student lessons")
    public ResponseEntity<ApiResponse<LessonNoteResponse>> createLessonNote(
            @Valid @RequestBody CreateLessonNoteRequest request
    ) {
        Long callerId = SecurityUtils.getCurrentUserId();
        LessonNoteResponse response = lessonNoteService.createLessonNote(request, callerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success( "Lesson note created successfully",response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Operation(summary = "Update lesson note", description = "Instructors can update their own lesson notes")
    public ResponseEntity<ApiResponse<LessonNoteResponse>> updateLessonNote(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLessonNoteRequest request
    ) {
        Long callerId = SecurityUtils.getCurrentUserId();
        LessonNoteResponse response = lessonNoteService.updateLessonNote(id, request, callerId);
        return ResponseEntity.ok(ApiResponse.success( "Lesson note updated successfully",response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get lesson note", description = "Get details of a specific lesson note")
    public ResponseEntity<ApiResponse<LessonNoteResponse>> getLessonNote(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        LessonNoteResponse response = lessonNoteService.getLessonNote(id, userId, role);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/student/{studentId}")
    @Operation(summary = "Get student's lesson notes", description = "Get all lesson notes for a specific student")
    public ResponseEntity<ApiResponse<Page<LessonNoteResponse>>> getStudentNotes(
            @PathVariable Long studentId,
            Pageable pageable
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        Page<LessonNoteResponse> notes = lessonNoteService.getStudentNotes(studentId, pageable, userId, role);
        return ResponseEntity.ok(ApiResponse.success(notes));
    }

    @GetMapping("/instructor/{instructorId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    @Operation(summary = "Get instructor's lesson notes", description = "Get all lesson notes created by an instructor")
    public ResponseEntity<ApiResponse<Page<LessonNoteResponse>>> getInstructorNotes(
            @PathVariable Long instructorId,
            Pageable pageable
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();
        Page<LessonNoteResponse> notes = lessonNoteService.getInstructorNotes(instructorId, pageable, userId, role);
        return ResponseEntity.ok(ApiResponse.success(notes));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all lesson notes", description = "Admin can retrieve all lesson notes")
    public ResponseEntity<ApiResponse<Page<LessonNoteResponse>>> getAllNotes(Pageable pageable) {
        Page<LessonNoteResponse> notes = lessonNoteService.getAllNotes(pageable);
        return ResponseEntity.ok(ApiResponse.success(notes));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Operation(summary = "Delete lesson note", description = "Instructors can delete their own lesson notes")
    public ResponseEntity<ApiResponse<Void>> deleteLessonNote(@PathVariable Long id) {
        Long callerId = SecurityUtils.getCurrentUserId();
        lessonNoteService.deleteLessonNote(id, callerId);
        return ResponseEntity.ok(ApiResponse.success( "Lesson note deleted successfully",null));
    }
    /**
     * Upload a file attachment to a lesson note
     *
     * @param lessonNoteId Lesson note ID
     * @param file PDF file to upload (multipart/form-data)
     * @param description Optional description
     * @return Attachment metadata response
     */
    @PostMapping("/{lessonNoteId}/attachments")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(
            summary = "Upload attachment",
            description = "Upload a PDF file to a lesson note. Max size: 50 MB.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = String.class))
            )
    )
    public ResponseEntity<ApiResponse<AttachmentResponse>> uploadAttachment(
            @PathVariable Long lessonNoteId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {
        try {
            AttachmentResponse response = attachmentService.uploadAttachment(lessonNoteId, file, description);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (IOException e) {
            log.error("File upload failed", e);
            throw new RuntimeException("File upload failed: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            log.error("Hash calculation failed", e);
            throw new RuntimeException("File processing failed");
        }
    }

    /**
     * Get all attachments for a lesson note
     *
     * @param lessonNoteId Lesson note ID
     * @return List of attachment responses
     */
    @GetMapping("/{lessonNoteId}/attachments")
    @Operation(summary = "Get attachments", description = "Retrieve all active attachments for a lesson note")
    public ResponseEntity<ApiResponse<List<AttachmentResponse>>> getAttachments(
            @PathVariable Long lessonNoteId) {
        return ResponseEntity.ok(ApiResponse.success(
                attachmentService.getAttachments(lessonNoteId)));
    }

    /**
     * Get attachments with pagination
     *
     * @param lessonNoteId Lesson note ID
     * @param pageable Pagination info
     * @return Page of attachment responses
     */
    @GetMapping("/{lessonNoteId}/attachments/page")
    @Operation(summary = "Get attachments (paginated)")
    public ResponseEntity<ApiResponse<Page<AttachmentResponse>>> getAttachmentsPaginated(
            @PathVariable Long lessonNoteId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                attachmentService.getAttachmentsPaginated(lessonNoteId, pageable)));
    }

    /**
     * Download an attachment file
     *
     * @param lessonNoteId Lesson note ID
     * @param attachmentId Attachment ID
     * @return File resource for download
     */
    @GetMapping("/{lessonNoteId}/attachments/{attachmentId}/download")
    @Operation(summary = "Download attachment", description = "Download a PDF attachment file")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long lessonNoteId,
            @PathVariable Long attachmentId) {
        try {
            Resource resource = attachmentService.downloadAttachment(lessonNoteId, attachmentId);

            String filename = resource.getFilename() != null ? resource.getFilename() : "file.pdf";

            // RFC 6266/5987: an ASCII fallback plus filename*=UTF-8''..., so names with
            // non-ASCII characters (accents, "·") survive in every browser.
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                    .body(resource);
        } catch (IOException e) {
            log.error("File download failed", e);
            throw new ServiceUnavailableException("The file can't be fetched from storage right now - please try again later", e);
        }
    }

    /**
     * Delete an attachment
     *
     * @param lessonNoteId Lesson note ID
     * @param attachmentId Attachment ID
     * @return Success response
     */
    @DeleteMapping("/{lessonNoteId}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Delete attachment")
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(
            @PathVariable Long lessonNoteId,
            @PathVariable Long attachmentId) {
        attachmentService.deleteAttachment(lessonNoteId, attachmentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Replace an attachment with a new file
     *
     * @param lessonNoteId Lesson note ID
     * @param attachmentId Attachment ID to replace
     * @param file New PDF file
     * @param description Updated description
     * @return Updated attachment response
     */
    @PutMapping("/{lessonNoteId}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Replace attachment", description = "Replace an attachment with a new file")
    public ResponseEntity<ApiResponse<AttachmentResponse>> replaceAttachment(
            @PathVariable Long lessonNoteId,
            @PathVariable Long attachmentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {
        try {
            AttachmentResponse response = attachmentService.replaceAttachment(
                    lessonNoteId, attachmentId, file, description);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IOException e) {
            log.error("File upload failed", e);
            throw new RuntimeException("File upload failed: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            log.error("Hash calculation failed", e);
            throw new RuntimeException("File processing failed");
        }
    }




}
