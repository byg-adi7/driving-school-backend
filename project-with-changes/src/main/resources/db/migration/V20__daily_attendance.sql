-- ============================================================
-- schools: where the school is, how close a check-in must be, and which
-- calendar day "today" is for it (one check-in per person per local day).
-- ============================================================
ALTER TABLE schools ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE schools ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE schools ADD COLUMN attendance_radius_meters INT NOT NULL DEFAULT 150;
ALTER TABLE schools ADD COLUMN time_zone VARCHAR(64) NOT NULL DEFAULT 'Africa/Accra';

-- ============================================================
-- daily_attendance: one row per person per school-local day - a GPS
-- check-in (location, accuracy and distance kept for the audit trail) or
-- an entry made by staff. Separate from live-session `attendances`.
-- ============================================================
CREATE TABLE daily_attendance (
    id                   BIGSERIAL PRIMARY KEY,
    school_id            BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    user_id              BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role                 VARCHAR(20) NOT NULL,
    attendance_date      DATE NOT NULL,
    status               VARCHAR(30) NOT NULL,
    source               VARCHAR(20) NOT NULL,
    checked_in_at        TIMESTAMP,
    latitude             DOUBLE PRECISION,
    longitude            DOUBLE PRECISION,
    accuracy_meters      DOUBLE PRECISION,
    distance_meters      DOUBLE PRECISION,
    confirmed_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    confirmed_at         TIMESTAMP,
    recorded_by_user_id  BIGINT REFERENCES users(id) ON DELETE SET NULL,
    reason               VARCHAR(500),
    created_at           TIMESTAMP NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_daily_attendance_user_date UNIQUE (user_id, attendance_date)
);
CREATE INDEX idx_daily_attendance_school_date ON daily_attendance(school_id, attendance_date);
