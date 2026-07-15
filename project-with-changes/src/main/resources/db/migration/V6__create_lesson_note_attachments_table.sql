-- Create lesson_note_attachments table
-- Stores file attachment metadata for lesson notes

CREATE TABLE IF NOT EXISTS lesson_note_attachments (
                                                       id BIGSERIAL PRIMARY KEY,

    -- Reference to lesson note
                                                       lesson_note_id BIGINT NOT NULL REFERENCES lesson_notes(id) ON DELETE CASCADE,

    -- File information
                                                       file_name VARCHAR(255) NOT NULL,
                                                       file_size BIGINT NOT NULL,
                                                       file_type VARCHAR(50) NOT NULL,
                                                       file_path VARCHAR(500) NOT NULL,
                                                       file_hash VARCHAR(64),

    -- Who uploaded
                                                       uploaded_by_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Metadata
                                                       description VARCHAR(500),
                                                       download_count BIGINT NOT NULL DEFAULT 0,
                                                       is_active BOOLEAN NOT NULL DEFAULT true,

    -- Timestamps
                                                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
                                                       CONSTRAINT uk_file_hash_lesson_note UNIQUE (file_hash, lesson_note_id)
);

-- Create indexes for common queries
CREATE INDEX IF NOT EXISTS idx_attachment_lesson_note
    ON lesson_note_attachments(lesson_note_id);

CREATE INDEX IF NOT EXISTS idx_attachment_uploaded_by
    ON lesson_note_attachments(uploaded_by_id);

CREATE INDEX IF NOT EXISTS idx_attachment_created_at
    ON lesson_note_attachments(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_attachment_is_active
    ON lesson_note_attachments(is_active);

-- Add comment for documentation
COMMENT ON TABLE lesson_note_attachments IS 'Stores file attachments for lesson notes. Lecturers can upload PDFs to supplement notes.';
COMMENT ON COLUMN lesson_note_attachments.file_hash IS 'SHA-256 hash for deduplication and integrity verification';
COMMENT ON COLUMN lesson_note_attachments.file_path IS 'Relative path for local storage or full URL for S3';
COMMENT ON COLUMN lesson_note_attachments.download_count IS 'Tracks number of downloads for analytics';
COMMENT ON COLUMN lesson_note_attachments.is_active IS 'Soft delete flag - false means attachment is no longer available';