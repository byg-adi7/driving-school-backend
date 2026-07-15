-- Reconciles courses/video_lessons/quizzes/quiz_questions/quiz_submissions with
-- their current JPA entities. These tables were originally created in V1 for an
-- earlier, simpler course model (school-owned, no publish workflow); the entities
-- were since redesigned around instructor-owned courses with a draft/publish
-- workflow, but no migration was ever written to match. Only ever caught locally
-- because dev's ddl-auto=update silently patched each developer's own database;
-- ddl-auto=validate (used in prod) fails outright against a genuinely fresh
-- database. No production data exists in these tables (pre-launch), so this
-- migration drops the now-unused columns rather than leaving dead weight.

-- ============================================================
-- courses: was school-owned (school_id/price/license_type/duration_hours/
-- is_active), is now instructor-owned with a draft/publish workflow.
-- School affiliation is still derivable via instructor_profiles.school_id.
-- ============================================================
ALTER TABLE courses RENAME COLUMN name TO title;

-- NOT NULL with no default/backfill is safe here only because the courses
-- table has no rows (pre-launch, confirmed no production data). If this table
-- ever holds real data, this migration must be revised to backfill first.
ALTER TABLE courses
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN published_at TIMESTAMP,
    ADD COLUMN instructor_id BIGINT NOT NULL REFERENCES instructor_profiles(id) ON DELETE CASCADE;

ALTER TABLE courses
    DROP COLUMN school_id,
    DROP COLUMN duration_hours,
    DROP COLUMN price,
    DROP COLUMN license_type,
    DROP COLUMN is_active;

CREATE INDEX IF NOT EXISTS idx_courses_instructor_id ON courses(instructor_id);
CREATE INDEX IF NOT EXISTS idx_courses_status ON courses(status);

-- ============================================================
-- video_lessons: order_index -> lesson_order (renamed), add published
-- ============================================================
ALTER TABLE video_lessons RENAME COLUMN order_index TO lesson_order;
ALTER TABLE video_lessons ADD COLUMN published BOOLEAN NOT NULL DEFAULT FALSE;

-- ============================================================
-- quizzes: pass_score -> passing_score, duration_minutes -> time_limit_minutes
-- (renamed), add max_attempts and published
-- ============================================================
ALTER TABLE quizzes RENAME COLUMN pass_score TO passing_score;
ALTER TABLE quizzes RENAME COLUMN duration_minutes TO time_limit_minutes;
ALTER TABLE quizzes
    ADD COLUMN max_attempts INT NOT NULL DEFAULT 1,
    ADD COLUMN published BOOLEAN NOT NULL DEFAULT FALSE;

-- ============================================================
-- quiz_questions: add points and question_order
-- ============================================================
ALTER TABLE quiz_questions
    ADD COLUMN points INT NOT NULL DEFAULT 1,
    ADD COLUMN question_order INT NOT NULL DEFAULT 1;

-- ============================================================
-- quiz_submissions: add attempt_number, answers, passed
-- ============================================================
ALTER TABLE quiz_submissions
    ADD COLUMN attempt_number INT NOT NULL DEFAULT 1,
    ADD COLUMN answers TEXT,
    ADD COLUMN passed BOOLEAN NOT NULL DEFAULT FALSE;
