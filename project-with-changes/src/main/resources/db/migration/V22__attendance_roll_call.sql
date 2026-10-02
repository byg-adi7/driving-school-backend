-- ============================================================
-- daily_attendance: staff roll call. A check-in only proves someone came
-- through the gate; an instructor or admin going through the day's list
-- (and marking anyone who signed in and left as absent) records who
-- reviewed each entry and when.
-- ============================================================
ALTER TABLE daily_attendance ADD COLUMN reviewed_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE daily_attendance ADD COLUMN reviewed_at TIMESTAMP;
