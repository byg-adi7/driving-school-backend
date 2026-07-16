-- Practical lesson booking is instructor-initiated only; students no longer
-- self-book, so pickup location is a frontend constant (always the driving
-- school) rather than API state.
ALTER TABLE bookings DROP COLUMN IF EXISTS pickup_location;

-- ============================================================
-- notifications: add read/unread state so students can see which
-- notifications (e.g. "your lesson has been scheduled") they've already
-- viewed. A nullable timestamp is the read-state signal, consistent with how
-- sent_at already works - no redundant boolean.
-- ============================================================
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS read_at TIMESTAMP;

-- Some environments have a channel CHECK constraint (from an earlier
-- Hibernate ddl-auto=update pass, never captured in a migration) limited to
-- the pre-existing EMAIL/SMS/PUSH values. Recreate it explicitly so the new
-- IN_APP channel is allowed everywhere, regardless of whether the constraint
-- already existed.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_channel_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_channel_check
    CHECK (channel::text = ANY (ARRAY['EMAIL', 'SMS', 'PUSH', 'IN_APP']::text[]));

-- ============================================================
-- practical_lesson_routes: was wired to an unvalidated live_session_id even
-- though a route is really about a specific Booking (the physical lesson),
-- not a virtual meeting. No production data exists in this table (pre-launch,
-- see V9), so this rewires the column directly rather than backfilling.
-- ============================================================
ALTER TABLE practical_lesson_routes DROP COLUMN IF EXISTS live_session_id;
ALTER TABLE practical_lesson_routes ADD COLUMN booking_id BIGINT NOT NULL;
ALTER TABLE practical_lesson_routes
    ADD CONSTRAINT fk_route_booking FOREIGN KEY (booking_id) REFERENCES bookings(id);
CREATE INDEX idx_route_booking ON practical_lesson_routes(booking_id);

-- ============================================================
-- lesson_notes: same wrong-concept live_session_id, but notes are already
-- keyed to the instructor/student pair directly and must stay accessible
-- independent of any booking's existence - so the replacement is an
-- OPTIONAL cross-reference, not a required FK.
-- ============================================================
ALTER TABLE lesson_notes DROP COLUMN IF EXISTS live_session_id;
ALTER TABLE lesson_notes ADD COLUMN booking_id BIGINT;
ALTER TABLE lesson_notes
    ADD CONSTRAINT fk_lesson_note_booking FOREIGN KEY (booking_id) REFERENCES bookings(id);
CREATE INDEX idx_lesson_notes_booking ON lesson_notes(booking_id);

-- ============================================================
-- driving_assessments: add an optional traceability link to the booking the
-- assessment was for. Entity-only addition for now - no service/controller
-- layer exists yet for this table.
-- ============================================================
ALTER TABLE driving_assessments ADD COLUMN booking_id BIGINT;
ALTER TABLE driving_assessments
    ADD CONSTRAINT fk_driving_assessment_booking FOREIGN KEY (booking_id) REFERENCES bookings(id);
CREATE INDEX idx_driving_assessments_booking ON driving_assessments(booking_id);
