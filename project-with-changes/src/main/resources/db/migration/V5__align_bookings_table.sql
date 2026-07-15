-- Align bookings table with Booking JPA entity

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'bookings' AND column_name = 'start_time'
    ) THEN
        ALTER TABLE bookings RENAME COLUMN start_time TO scheduled_at;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'bookings' AND column_name = 'end_time'
    ) THEN
        ALTER TABLE bookings RENAME COLUMN end_time TO end_at;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'bookings' AND column_name = 'lesson_type'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'bookings' AND column_name = 'booking_type'
    ) THEN
        ALTER TABLE bookings RENAME COLUMN lesson_type TO booking_type;
    END IF;
END $$;

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS school_id BIGINT REFERENCES schools(id);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS payment_id BIGINT REFERENCES payments(id);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS duration_minutes INTEGER;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS pickup_location VARCHAR(500);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS booking_type VARCHAR(30);

UPDATE bookings b
SET school_id = sp.school_id
FROM student_profiles sp
WHERE b.student_id = sp.id
  AND b.school_id IS NULL;

UPDATE bookings
SET duration_minutes = GREATEST(
        1,
        EXTRACT(EPOCH FROM (end_at - scheduled_at))::INTEGER / 60
    )
WHERE duration_minutes IS NULL
  AND scheduled_at IS NOT NULL
  AND end_at IS NOT NULL;

UPDATE bookings
SET booking_type = 'ROAD_LESSON'
WHERE booking_type IS NULL OR TRIM(booking_type) = '';

ALTER TABLE bookings ALTER COLUMN vehicle_id DROP NOT NULL;
ALTER TABLE bookings ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE bookings ALTER COLUMN duration_minutes SET NOT NULL;
ALTER TABLE bookings ALTER COLUMN booking_type SET NOT NULL;

DROP INDEX IF EXISTS idx_bookings_start_time;
CREATE INDEX IF NOT EXISTS idx_bookings_scheduled_at ON bookings(scheduled_at);
