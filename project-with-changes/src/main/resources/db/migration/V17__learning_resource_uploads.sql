-- ============================================================
-- Course resources can now be an uploaded file (stored through StorageService -
-- Cloudinary in prod - exactly like lesson-note attachments), not only an
-- external link. Before this, an instructor had to host a PDF elsewhere and
-- paste its URL.
--
-- A resource is EITHER an external link (file_url) OR an uploaded file
-- (storage_path + its metadata) - never both, never neither. Existing rows are
-- all links, so they already satisfy the check.
-- ============================================================
ALTER TABLE learning_resources ALTER COLUMN file_url DROP NOT NULL;

ALTER TABLE learning_resources ADD COLUMN IF NOT EXISTS storage_path VARCHAR(500);
ALTER TABLE learning_resources ADD COLUMN IF NOT EXISTS file_name VARCHAR(255);
ALTER TABLE learning_resources ADD COLUMN IF NOT EXISTS file_size BIGINT;
ALTER TABLE learning_resources ADD COLUMN IF NOT EXISTS content_type VARCHAR(100);

ALTER TABLE learning_resources
    ADD CONSTRAINT ck_learning_resources_link_or_upload
    CHECK ((file_url IS NOT NULL) <> (storage_path IS NOT NULL));
