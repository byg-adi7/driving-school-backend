package com.drivingschool.backend.lesson.note.service;

import com.drivingschool.backend.common.exception.BadRequestException;
import com.drivingschool.backend.common.exception.ResourceNotFoundException;
import com.drivingschool.backend.common.util.SecurityUtils;
import com.drivingschool.backend.lesson.note.dto.AttachmentResponse;
import com.drivingschool.backend.lesson.note.entity.LessonNote;
import com.drivingschool.backend.lesson.note.entity.LessonNoteAttachment;
import com.drivingschool.backend.lesson.note.repository.LessonNoteAttachmentRepository;
import com.drivingschool.backend.lesson.note.repository.LessonNoteRepository;
import com.drivingschool.backend.lesson.note.validator.LessonNoteValidator;
import com.drivingschool.backend.storage.StorageService;
import com.drivingschool.backend.storage.StoredFile;
import com.drivingschool.backend.user.entity.User;
import com.drivingschool.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LessonNoteAttachmentService
 *
 * Handles business logic for lesson note attachments.
 *
 * Responsibilities:
 * - Upload files to lesson notes
 * - Retrieve attachment metadata
 * - Download files with access control
 * - Delete attachments
 * - Manage attachment permissions
 *
 * Security:
 * - Only lecturer who created note or ADMIN can delete
 * - Both lecturer and student can download
 * - ADMIN can see all
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LessonNoteAttachmentService {

    private final LessonNoteAttachmentRepository attachmentRepository;
    private final LessonNoteRepository lessonNoteRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final LessonNoteValidator validator;

    /**
     * Upload a file attachment to a lesson note
     *
     * @param lessonNoteId ID of the lesson note
     * @param file The file to upload
     * @param description Optional description
     * @return AttachmentResponse with metadata
     * @throws IOException if file storage fails
     * @throws NoSuchAlgorithmException if hash calculation fails
     */
    @Transactional
    public AttachmentResponse uploadAttachment(Long lessonNoteId, MultipartFile file, String description)
            throws IOException, NoSuchAlgorithmException {

        // Verify lesson note exists
        LessonNote lessonNote = lessonNoteRepository.findById(lessonNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson note not found"));

        // Get current user (uploader)
        Long currentUserId = SecurityUtils.getCurrentUserId();
        User uploader = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Verify user is authorized to upload to this lesson note
        verifyUploadPermission(lessonNote, currentUserId);

        // Upload file
        StoredFile metadata;
        try {
            metadata = storageService.store(file, "lesson-notes", String.valueOf(lessonNoteId));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        // Check for duplicate file (deduplication)
        if (metadata.getFileHash() != null &&
                attachmentRepository.existsByFileHash(metadata.getFileHash())) {
            log.info("Duplicate file detected (hash: {}), reusing existing attachment", metadata.getFileHash());
        }

        // Create attachment record
        LessonNoteAttachment attachment = LessonNoteAttachment.builder()
                .lessonNote(lessonNote)
                .fileName(metadata.getFileName())
                .fileSize(metadata.getFileSize())
                .fileType(metadata.getContentType())
                .filePath(metadata.getStoragePath())
                .fileHash(metadata.getFileHash())
                .uploadedBy(uploader)
                .description(description)
                .downloadCount(0L)
                .isActive(true)
                .build();

        LessonNoteAttachment saved = attachmentRepository.save(attachment);

        log.info("Attachment uploaded to lesson note {}: {} (ID: {})",
                lessonNoteId, metadata.getFileName(), saved.getId());

        return mapToResponse(saved);
    }

    /**
     * Get all active attachments for a lesson note
     *
     * @param lessonNoteId ID of the lesson note
     * @return List of active attachments
     */
    @Transactional(readOnly = true)
    public List<AttachmentResponse> getAttachments(Long lessonNoteId) {
        LessonNote lessonNote = lessonNoteRepository.findById(lessonNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson note not found"));
        validator.validateReadAccess(lessonNote, SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentUserRole());

        List<LessonNoteAttachment> attachments = attachmentRepository.findActiveByLessonNoteId(lessonNoteId);

        return attachments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get paginated attachments for a lesson note
     *
     * @param lessonNoteId ID of the lesson note
     * @param pageable pagination info
     * @return Page of attachments
     */
    @Transactional(readOnly = true)
    public Page<AttachmentResponse> getAttachmentsPaginated(Long lessonNoteId, Pageable pageable) {
        LessonNote lessonNote = lessonNoteRepository.findById(lessonNoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson note not found"));
        validator.validateReadAccess(lessonNote, SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentUserRole());

        return attachmentRepository.findByLessonNoteId(lessonNoteId, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Download an attachment file
     * Increments download counter and checks access permissions
     *
     * @param lessonNoteId ID of the lesson note
     * @param attachmentId ID of the attachment
     * @return Resource for file streaming
     * @throws IOException if file cannot be read
     */
    @Transactional
    public Resource downloadAttachment(Long lessonNoteId, Long attachmentId) throws IOException {
        // Fetch attachment with lesson note
        LessonNoteAttachment attachment = attachmentRepository.findByIdWithLessonNote(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));

        // Verify attachment belongs to the specified lesson note
        if (!attachment.getLessonNote().getId().equals(lessonNoteId)) {
            throw new BadRequestException("Attachment does not belong to this lesson note");
        }

        // Verify attachment is active
        if (!attachment.getIsActive()) {
            throw new BadRequestException("Attachment is no longer available");
        }

        // Verify download permission
        verifyDownloadPermission(attachment);

        // Increment download counter
        attachment.incrementDownloadCount();
        attachmentRepository.save(attachment);

        log.info("Attachment {} downloaded (total downloads: {})",
                attachmentId, attachment.getDownloadCount());

        // Return resource for streaming
        return storageService.load(attachment.getFilePath());
    }

    /**
     * Delete an attachment
     * Only the uploader or ADMIN can delete
     *
     * @param lessonNoteId ID of the lesson note
     * @param attachmentId ID of the attachment
     */
    @Transactional
    public void deleteAttachment(Long lessonNoteId, Long attachmentId) {
        // Fetch attachment
        LessonNoteAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));

        // Verify attachment belongs to the specified lesson note
        if (!attachment.getLessonNote().getId().equals(lessonNoteId)) {
            throw new BadRequestException("Attachment does not belong to this lesson note");
        }

        // Verify deletion permission
        verifyDeletePermission(attachment);

        // Delete file from storage
        try {
            storageService.delete(attachment.getFilePath());
        } catch (IOException e) {
            log.warn("Failed to delete physical file: {}", attachment.getFilePath(), e);
            // Continue anyway, delete database record
        }

        // Delete attachment record
        attachmentRepository.delete(attachment);

        log.info("Attachment {} deleted for lesson note {}", attachmentId, lessonNoteId);
    }

    /**
     * Replace an attachment with a new file
     * Deletes old file and uploads new one
     *
     * @param lessonNoteId ID of the lesson note
     * @param attachmentId ID of the attachment to replace
     * @param newFile The new file
     * @param description Updated description
     * @return AttachmentResponse for the new attachment
     * @throws IOException if file operations fail
     */
    @Transactional
    public AttachmentResponse replaceAttachment(Long lessonNoteId, Long attachmentId,
                                                MultipartFile newFile, String description)
            throws IOException, NoSuchAlgorithmException {

        // Get existing attachment
        LessonNoteAttachment oldAttachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));

        // Verify it belongs to the lesson note
        if (!oldAttachment.getLessonNote().getId().equals(lessonNoteId)) {
            throw new BadRequestException("Attachment does not belong to this lesson note");
        }

        // Verify replace permission
        verifyDeletePermission(oldAttachment);

        // Delete old file
        try {
            storageService.delete(oldAttachment.getFilePath());
        } catch (IOException e) {
            log.warn("Failed to delete old file: {}", oldAttachment.getFilePath(), e);
        }

        // Upload new file
        StoredFile metadata;
        try {
            metadata = storageService.store(newFile, "lesson-notes", String.valueOf(lessonNoteId));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        // Update attachment record
        Long currentUserId = SecurityUtils.getCurrentUserId();
        User uploader = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        oldAttachment.setFileName(metadata.getFileName());
        oldAttachment.setFileSize(metadata.getFileSize());
        oldAttachment.setFileType(metadata.getContentType());
        oldAttachment.setFilePath(metadata.getStoragePath());
        oldAttachment.setFileHash(metadata.getFileHash());
        oldAttachment.setUploadedBy(uploader);
        oldAttachment.setDescription(description);
        oldAttachment.setDownloadCount(0L);  // Reset download counter

        LessonNoteAttachment updated = attachmentRepository.save(oldAttachment);

        log.info("Attachment {} replaced with new file: {}", attachmentId, metadata.getFileName());

        return mapToResponse(updated);
    }

    /**
     * Verify user has permission to upload to a lesson note
     * Only lecturer who created the note or ADMIN can upload
     *
     * @param lessonNote The lesson note
     * @param userId ID of current user
     */
    private void verifyUploadPermission(LessonNote lessonNote, Long userId) {
        String role = SecurityUtils.getCurrentUserRole();

        if ("ADMIN".equals(role)) {
            return;  // Admin can always upload
        }

        if ("INSTRUCTOR".equals(role) && lessonNote.getInstructor().getUser().getId().equals(userId)) {
            return;  // Instructor who created note can upload
        }

        throw new BadRequestException("You do not have permission to upload files to this lesson note");
    }

    /**
     * Verify user has permission to download attachment
     * Lecturer who created note, student in note, or ADMIN can download
     *
     * @param attachment The attachment
     */
    private void verifyDownloadPermission(LessonNoteAttachment attachment) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();

        if ("ADMIN".equals(role)) {
            return;  // Admin can always download
        }

        LessonNote lessonNote = attachment.getLessonNote();

        // Lecturer can download
        if ("INSTRUCTOR".equals(role) && lessonNote.getInstructor().getUser().getId().equals(currentUserId)) {
            return;
        }

        // Student can download
        if ("STUDENT".equals(role) && lessonNote.getStudent().getUser().getId().equals(currentUserId)) {
            return;
        }

        throw new BadRequestException("You do not have permission to download this attachment");
    }

    /**
     * Verify user has permission to delete attachment
     * Only uploader or ADMIN can delete
     *
     * @param attachment The attachment
     */
    private void verifyDeletePermission(LessonNoteAttachment attachment) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentUserRole();

        if ("ADMIN".equals(role)) {
            return;  // Admin can always delete
        }

        if (attachment.getUploadedBy().getId().equals(currentUserId)) {
            return;  // Uploader can delete
        }

        throw new BadRequestException("You do not have permission to delete this attachment");
    }

    /**
     * Map entity to DTO
     *
     * @param attachment The attachment entity
     * @return AttachmentResponse DTO
     */
    private AttachmentResponse mapToResponse(LessonNoteAttachment attachment) {
        AttachmentResponse response = AttachmentResponse.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .fileSize(attachment.getFileSize())
                .fileType(attachment.getFileType())
                .createdAt(attachment.getCreatedAt())
                .uploadedById(attachment.getUploadedBy().getId())
                .uploadedByName(attachment.getUploadedBy().getDisplayName())
                .description(attachment.getDescription())
                .downloadCount(attachment.getDownloadCount())
                .isActive(attachment.getIsActive())
                .downloadUrl(String.format("/api/v1/lesson-notes/%d/attachments/%d/download",
                        attachment.getLessonNote().getId(),
                        attachment.getId()))
                .build();

        response.setFileSizeFormatted(response.formatFileSize());
        return response;
    }
}
