-- A video lesson can now be course materials (PDFs) only, with no video.
ALTER TABLE video_lessons ALTER COLUMN video_url DROP NOT NULL;
