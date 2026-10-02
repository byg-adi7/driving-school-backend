-- ============================================================
-- daily_attendance: students check in automatically when they're at the
-- school; otherwise the check-in waits for an instructor, and
-- confirmation_reason says why. Students also say what they're there for
-- (PRACTICAL or THEORY) and, optionally, the topic.
-- ============================================================
ALTER TABLE daily_attendance ADD COLUMN lesson_type VARCHAR(20);
ALTER TABLE daily_attendance ADD COLUMN topic VARCHAR(200);
ALTER TABLE daily_attendance ADD COLUMN confirmation_reason VARCHAR(40);
