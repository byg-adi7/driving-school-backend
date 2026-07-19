-- ============================================================
-- student_game_stats: one aggregate row per student - total points and
-- weekly-streak state. Streak is evaluated lazily (no scheduled job); see
-- StudentGameStats.applyQualifyingWeek() - last_activity_week_start (Monday
-- of the ISO week of the student's last qualifying completed booking) is the
-- only state needed to decide hold/increment/reset on the next event.
-- ============================================================
CREATE TABLE IF NOT EXISTS student_game_stats (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    total_points INT NOT NULL DEFAULT 0,
    current_streak_weeks INT NOT NULL DEFAULT 0,
    longest_streak_weeks INT NOT NULL DEFAULT 0,
    last_activity_week_start DATE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
ALTER TABLE student_game_stats
    ADD CONSTRAINT uk_student_game_stats_student_id UNIQUE (student_id);
ALTER TABLE student_game_stats
    ADD CONSTRAINT fk_student_game_stats_student FOREIGN KEY (student_id) REFERENCES student_profiles(id);

-- ============================================================
-- points_transactions: append-only points ledger. The unique constraint on
-- (source_type, source_id) is the actual enforcement of "award once per
-- triggering event" - for QUIZ_PASSED, source_id is the quiz's id (not the
-- submission's), so a second passing attempt at the same quiz can never
-- insert a second row here. This is deliberate, not just defensive: it is
-- how "once per quiz, not once per attempt" is actually enforced.
-- ============================================================
CREATE TABLE IF NOT EXISTS points_transactions (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    points INT NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
ALTER TABLE points_transactions
    ADD CONSTRAINT uk_points_transactions_source UNIQUE (source_type, source_id);
ALTER TABLE points_transactions
    ADD CONSTRAINT fk_points_transactions_student FOREIGN KEY (student_id) REFERENCES student_profiles(id);
CREATE INDEX idx_points_transactions_student_id ON points_transactions(student_id);

-- ============================================================
-- badge_awards: which named badges (fixed v1 catalog - see BadgeType enum,
-- not admin-configurable/DB-driven) each student has earned. The unique
-- constraint is both the "already awarded" idempotency guard and the natural
-- business rule (a badge is a one-time achievement per student).
-- ============================================================
CREATE TABLE IF NOT EXISTS badge_awards (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    badge VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
ALTER TABLE badge_awards
    ADD CONSTRAINT uk_badge_awards_student_badge UNIQUE (student_id, badge);
ALTER TABLE badge_awards
    ADD CONSTRAINT fk_badge_awards_student FOREIGN KEY (student_id) REFERENCES student_profiles(id);
CREATE INDEX idx_badge_awards_student_id ON badge_awards(student_id);
