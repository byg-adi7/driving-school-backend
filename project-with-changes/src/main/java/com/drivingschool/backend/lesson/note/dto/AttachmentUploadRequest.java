package com.drivingschool.backend.lesson.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for file attachment upload requests
 * Used when uploading a file to a lesson note
 */
@Data
@NoArgsConstructor
public class AttachmentUploadRequest {

    /**
     * Optional description of the attachment
     * Example: "Key points from today's lesson"
     */
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
}