-- Soft-delete for user accounts. A deleted account is hidden (deleted_at set,
-- enabled=false blocks login via the existing UserDetails.isEnabled() check -
-- no changes needed to the authentication flow itself) rather than
-- SQL-deleted, since a real DELETE would cascade through
-- student_profiles/instructor_profiles and permanently destroy bookings,
-- quiz submissions, driving assessments, and lesson notes with no recovery.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_users_deleted_at ON users(deleted_at);
