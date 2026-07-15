package com.drivingschool.backend.lesson.note.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for file attachment responses
 * Returned when listing or retrieving attachments
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachmentResponse {

    /**
     * Unique identifier of the attachment
     */
    private Long id;

    /**
     * Original file name
     */
    private String fileName;

    /**
     * File size in bytes
     * Frontend can format this for display (e.g., "2.5 MB")
     */
    private Long fileSize;

    /**
     * MIME type
     */
    private String fileType;

    /**
     * When the file was uploaded
     */
    private LocalDateTime createdAt;

    /**
     * Who uploaded the file
     */
    private Long uploadedById;

    /**
     * Name of the person who uploaded
     */
    private String uploadedByName;

    /**
     * Optional description
     */
    private String description;

    /**
     * Number of times this file has been downloaded
     */
    private Long downloadCount;

    /**
     * Whether the attachment is active (can be downloaded)
     */
    @JsonProperty("isActive")
    private Boolean isActive;

    /**
     * URL to download the file
     * Relative path: /api/v1/lesson-notes/{noteId}/attachments/{attachmentId}/download
     */
    @JsonProperty("downloadUrl")
    private String downloadUrl;

    /**
     * File size formatted for human display
     * Example: "2.5 MB"
     */
    @JsonProperty("fileSizeFormatted")
    private String fileSizeFormatted;

    /**
     * Format file size for human-readable display
     * Converts bytes to KB, MB, GB as appropriate
     */
    public String formatFileSize() {
        if (fileSize == null) return "0 B";

        long size = fileSize;
        if (size <= 0) return "0 B";

        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int unitIndex = (int) (Math.log10(size) / Math.log10(1024));
        double displaySize = size / Math.pow(1024, unitIndex);

        return String.format("%.2f %s", displaySize, units[unitIndex]);
    }
}