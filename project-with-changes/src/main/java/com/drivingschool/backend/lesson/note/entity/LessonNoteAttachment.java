package com.drivingschool.backend.lesson.note.entity;

import com.drivingschool.backend.common.base.BaseEntity;
import com.drivingschool.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LessonNoteAttachment Entity
 *
 * Represents a file attachment (PDF) associated with a lesson note.
 * Lecturers can upload PDF files to supplement lesson notes.
 * Both lecturers and students can download the file.
 *
 * Features:
 * - Store file metadata (name, size, type, URL)
 * - Track who uploaded the file and when
 * - Support multiple attachments per lesson note
 * - Store file path for local disk or S3 URL
 *
 * Database Table: lesson_note_attachments
 * Relationships: ManyToOne with LessonNote and User
 */
@Entity
@Table(
        name = "lesson_note_attachments",
        indexes = {
                @Index(name = "idx_attachment_lesson_note", columnList = "lesson_note_id"),
                @Index(name = "idx_attachment_uploaded_by", columnList = "uploaded_by_id"),
                @Index(name = "idx_attachment_created_at", columnList = "created_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonNoteAttachment extends BaseEntity {

    /**
     * Reference to the lesson note this attachment belongs to
     * Cascade delete: if lesson note is deleted, attachment is deleted
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_note_id", nullable = false)
    private LessonNote lessonNote;

    /**
     * Original name of the uploaded file
     * Example: "driving-tips-2024.pdf"
     */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /**
     * File size in bytes
     * Used for UI display and download validation
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * MIME type of the file
     * Example: "application/pdf"
     */
    @Column(name = "file_type", nullable = false, length = 50)
    private String fileType;

    /**
     * Path to the stored file
     * - For local storage: relative path like "uploads/lesson-notes/2024/note_123_file.pdf"
     * - For S3: full S3 URL like "https://bucket.s3.amazonaws.com/uploads/..."
     */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /**
     * Unique identifier for the stored file
     * Used to prevent duplicate uploads and for versioning
     * Example: UUID of the file
     */
    @Column(name = "file_hash", length = 64)
    private String fileHash;

    /**
     * Who uploaded this file
     * References the User entity (typically the lecturer)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private User uploadedBy;

    // createdAt is inherited from BaseEntity to centralize auditing. (Previously
    // redeclared here with Hibernate's @CreationTimestamp, which shadowed BaseEntity's
    // Spring Data JPA auditing field of the same name and meant created_at never
    // actually got populated - see PRODUCTION_READINESS.md.)

    /**
     * Optional description or notes about the attachment
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Download count tracking
     * Used to monitor file usage
     */
    @Column(name = "download_count", nullable = false)
    @Builder.Default
    private Long downloadCount = 0L;

    /**
     * Whether this attachment is still available for download
     * Can be soft-deleted by setting to false instead of hard-delete
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Increment download counter
     * Called each time file is downloaded
     */
    public void incrementDownloadCount() {
        if (this.downloadCount == null) {
            this.downloadCount = 0L;
        }
        this.downloadCount++;
    }

    /**
     * Soft delete the attachment
     * File remains in storage but is marked as inactive
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * Reactivate a deactivated attachment
     */
    public void activate() {
        this.isActive = true;
    }
}