-- Follow-up from the production-readiness audit (see PRODUCTION_READINESS.md):
-- a handful of columns have been nullable in the database since V1 even
-- though their entities have always declared nullable=false, and two
-- entity-declared indexes were never created. Hibernate's ddl-auto=validate
-- doesn't check either of these (only column existence/broad type), so they
-- never surfaced as a startup failure - they're tightened here as a
-- deliberate follow-up, not because anything was broken.
--
-- Confirmed no production data exists in any of these tables (pre-launch,
-- same basis as V8/V9), so nullability is tightened directly with no
-- backfill needed.

ALTER TABLE video_lessons ALTER COLUMN video_url SET NOT NULL;
ALTER TABLE video_lessons ALTER COLUMN lesson_order SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_video_lessons_order ON video_lessons(course_id, lesson_order);

ALTER TABLE quizzes ALTER COLUMN passing_score SET NOT NULL;

ALTER TABLE quiz_questions ALTER COLUMN question_type SET NOT NULL;
ALTER TABLE quiz_questions ALTER COLUMN correct_answer SET NOT NULL;

ALTER TABLE quiz_submissions ALTER COLUMN status SET NOT NULL;

ALTER TABLE attendances ALTER COLUMN status SET NOT NULL;

ALTER TABLE lesson_question_submissions ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE notifications ALTER COLUMN body SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_schools_name ON schools(name);
CREATE INDEX IF NOT EXISTS idx_schools_active ON schools(active);
