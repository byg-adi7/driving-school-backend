package com.drivingschool.backend.lesson.note.repository;

import com.drivingschool.backend.lesson.note.entity.LessonNoteAttachment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for LessonNoteAttachment entity
 * Handles database operations for file attachments
 */
@Repository
public interface LessonNoteAttachmentRepository extends JpaRepository<LessonNoteAttachment, Long> {

    /**
     * Find all active attachments for a specific lesson note
     * @param lessonNoteId ID of the lesson note
     * @return List of active attachments ordered by creation date (newest first)
     */
    @Query("SELECT a FROM LessonNoteAttachment a " +
            "WHERE a.lessonNote.id = :lessonNoteId AND a.isActive = true " +
            "ORDER BY a.createdAt DESC")
    List<LessonNoteAttachment> findActiveByLessonNoteId(@Param("lessonNoteId") Long lessonNoteId);

    /**
     * Find all attachments (active and inactive) for a lesson note with pagination
     * @param lessonNoteId ID of the lesson note
     * @param pageable pagination info
     * @return Page of attachments
     */
    @Query("SELECT a FROM LessonNoteAttachment a " +
            "WHERE a.lessonNote.id = :lessonNoteId " +
            "ORDER BY a.createdAt DESC")
    Page<LessonNoteAttachment> findByLessonNoteId(@Param("lessonNoteId") Long lessonNoteId, Pageable pageable);

    /**
     * Find an attachment by ID with its lesson note loaded
     * @param id Attachment ID
     * @return Optional containing the attachment if found
     */
    @Query("SELECT a FROM LessonNoteAttachment a " +
            "JOIN FETCH a.lessonNote " +
            "WHERE a.id = :id")
    Optional<LessonNoteAttachment> findByIdWithLessonNote(@Param("id") Long id);

    /**
     * Check if a file with given hash already exists
     * Used to prevent duplicate file uploads
     * @param fileHash SHA-256 hash of file content
     * @return true if file hash exists
     */
    boolean existsByFileHash(String fileHash);

    /**
     * Find attachment by file hash
     * @param fileHash SHA-256 hash of file content
     * @return Optional containing the attachment if found
     */
    Optional<LessonNoteAttachment> findByFileHash(String fileHash);

    /**
     * Count active attachments for a lesson note
     * @param lessonNoteId ID of the lesson note
     * @return Count of active attachments
     */
    @Query("SELECT COUNT(a) FROM LessonNoteAttachment a " +
            "WHERE a.lessonNote.id = :lessonNoteId AND a.isActive = true")
    long countActiveByLessonNoteId(@Param("lessonNoteId") Long lessonNoteId);

    /**
     * Find attachments uploaded by a specific user
     * @param uploadedById ID of the user
     * @param pageable pagination info
     * @return Page of attachments
     */
    @Query("SELECT a FROM LessonNoteAttachment a " +
            "WHERE a.uploadedBy.id = :uploadedById " +
            "ORDER BY a.createdAt DESC")
    Page<LessonNoteAttachment> findByUploadedById(@Param("uploadedById") Long uploadedById, Pageable pageable);

    /**
     * Delete all attachments for a lesson note
     * Called when lesson note is deleted
     * @param lessonNoteId ID of the lesson note
     * @return Number of rows deleted
     */
    @Query("DELETE FROM LessonNoteAttachment a WHERE a.lessonNote.id = :lessonNoteId")
    int deleteByLessonNoteId(@Param("lessonNoteId") Long lessonNoteId);
}