-- ============================================================
-- users: mark exactly one permanent bootstrap/super-admin row. Only this
-- account can create/delete schools and other admins directly, is never
-- itself deletable, and owns no school. BootstrapAdminInitializer sets this
-- on creation and self-heals it onto the pre-existing row on every startup
-- (no SQL migration can know the env-configured bootstrap email). The
-- partial unique index enforces "exactly one" at the DB level too.
-- ============================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS bootstrap_admin BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_single_bootstrap_admin ON users (bootstrap_admin) WHERE bootstrap_admin = TRUE;

-- ============================================================
-- schools: every school now has exactly one owning admin, mandatory and
-- unique in both directions. Deleting the owning admin's User row cascades
-- straight through to the school (SchoolAdminCascadeDeletionService only
-- ever calls userRepository.delete() on the admin, never
-- schoolRepository.delete() directly).
-- ============================================================
ALTER TABLE schools ADD COLUMN owning_admin_id BIGINT;
ALTER TABLE schools
    ADD CONSTRAINT fk_schools_owning_admin FOREIGN KEY (owning_admin_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE schools ADD CONSTRAINT uk_schools_owning_admin UNIQUE (owning_admin_id);

-- ============================================================
-- student_profiles.school_id / instructor_profiles.school_id: were
-- ON DELETE RESTRICT since V1, which blocked deleting a school entirely
-- while it had any student/instructor. School deletion is now a real,
-- intentional operation (bootstrap-direct or bootstrap-approved), so
-- RESTRICT must become CASCADE for it to actually succeed instead of
-- failing with a foreign key violation.
--
-- This must run before the orphan cleanup below, so that cleanup can
-- actually cascade instead of hitting the same RESTRICT violation itself.
-- ============================================================
ALTER TABLE student_profiles DROP CONSTRAINT IF EXISTS student_profiles_school_id_fkey;
ALTER TABLE student_profiles
    ADD CONSTRAINT fk_student_profiles_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE;

ALTER TABLE instructor_profiles DROP CONSTRAINT IF EXISTS instructor_profiles_school_id_fkey;
ALTER TABLE instructor_profiles
    ADD CONSTRAINT fk_instructor_profiles_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE;

-- ============================================================
-- bookings.school_id: had no ON DELETE clause at all (added in V5 with no
-- cascade, defaults to NO ACTION), same fix as above. bookings.student_id/
-- instructor_id already cascade from the profiles tables (V1), so this
-- closes the gap for whichever cascade path Postgres resolves first.
-- ============================================================
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_school_id_fkey;
ALTER TABLE bookings
    ADD CONSTRAINT fk_bookings_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE;

-- ============================================================
-- student_game_stats / points_transactions / badge_awards (V14): student_id
-- had no ON DELETE clause, so deleting a student_profiles row (including via
-- the school_id cascade above) would fail the moment a student had any
-- gamification history. CASCADE makes school deletion, and any other flow
-- that removes a student_profiles row, succeed end-to-end.
-- ============================================================
ALTER TABLE student_game_stats DROP CONSTRAINT IF EXISTS fk_student_game_stats_student;
ALTER TABLE student_game_stats
    ADD CONSTRAINT fk_student_game_stats_student FOREIGN KEY (student_id) REFERENCES student_profiles(id) ON DELETE CASCADE;

ALTER TABLE points_transactions DROP CONSTRAINT IF EXISTS fk_points_transactions_student;
ALTER TABLE points_transactions
    ADD CONSTRAINT fk_points_transactions_student FOREIGN KEY (student_id) REFERENCES student_profiles(id) ON DELETE CASCADE;

ALTER TABLE badge_awards DROP CONSTRAINT IF EXISTS fk_badge_awards_student;
ALTER TABLE badge_awards
    ADD CONSTRAINT fk_badge_awards_student FOREIGN KEY (student_id) REFERENCES student_profiles(id) ON DELETE CASCADE;

-- ============================================================
-- Pre-launch cleanup: this migration originally assumed no schools existed
-- yet and set owning_admin_id NOT NULL directly. By the time it actually
-- shipped, five schools already existed - all confirmed QA/route-testing
-- artifacts (e.g. "Route Test School 1784337672", "Sentry Test School
-- 1784381714"), none with any real owning admin to backfill from, since no
-- admin-school relationship existed anywhere before this migration. They're
-- deleted here (cascading through their student/instructor profiles,
-- bookings, and gamification rows via the FKs just tightened above) so the
-- NOT NULL constraint below can apply cleanly.
-- ============================================================
DELETE FROM schools WHERE owning_admin_id IS NULL;

ALTER TABLE schools ALTER COLUMN owning_admin_id SET NOT NULL;

-- ============================================================
-- school_deletion_requests: a non-bootstrap admin can never delete their
-- own school+account outright, only request it. This table is a permanent
-- audit trail - rows are never deleted by the application, only
-- transitioned PENDING -> APPROVED/REJECTED - which is why school_id and
-- requested_by_user_id are ON DELETE SET NULL rather than CASCADE: an
-- approved request's own row must survive the very deletion it caused.
-- school_name/requested_by_email are immutable snapshots captured at
-- request time so the row stays meaningful once those FKs go null.
-- reviewed_by_user_id needs no such treatment - its only possible value is
-- the bootstrap admin, who this migration guarantees can never be deleted.
-- ============================================================
CREATE TABLE IF NOT EXISTS school_deletion_requests (
    id                    BIGSERIAL PRIMARY KEY,
    school_id             BIGINT,
    school_name           VARCHAR(200) NOT NULL,
    status                VARCHAR(20) NOT NULL,
    requested_by_user_id  BIGINT,
    requested_by_email    VARCHAR(255) NOT NULL,
    reviewed_by_user_id   BIGINT,
    reviewed_at           TIMESTAMP,
    review_notes          VARCHAR(1000),
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP NOT NULL DEFAULT now()
);
ALTER TABLE school_deletion_requests
    ADD CONSTRAINT fk_school_deletion_requests_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE SET NULL;
ALTER TABLE school_deletion_requests
    ADD CONSTRAINT fk_school_deletion_requests_requested_by FOREIGN KEY (requested_by_user_id) REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE school_deletion_requests
    ADD CONSTRAINT fk_school_deletion_requests_reviewed_by FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id);

CREATE INDEX idx_school_deletion_requests_school_id ON school_deletion_requests(school_id);
CREATE INDEX idx_school_deletion_requests_status ON school_deletion_requests(status);

-- Only one PENDING request may exist per school at a time - a plain
-- UNIQUE(school_id) would also block a second request after the first is
-- resolved, which is wrong, so this is a partial index instead.
CREATE UNIQUE INDEX uk_school_deletion_requests_school_pending
    ON school_deletion_requests(school_id) WHERE status = 'PENDING';
