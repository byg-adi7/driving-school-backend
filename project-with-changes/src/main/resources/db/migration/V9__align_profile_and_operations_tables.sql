-- Reconciles instructor_profiles/student_profiles/driving_assessments/
-- learning_resources/vehicles/vehicle_locations/live_sessions/license_workflows/
-- notifications with their current JPA entities. Like V8, these tables were
-- created in V1 for an earlier design and were never migrated when the
-- entities were redesigned; dev's ddl-auto=update silently patched each
-- developer's own database, so this only ever surfaced against a genuinely
-- fresh database (ddl-auto=validate, used in prod). Confirmed no production
-- data exists in any of these tables (pre-launch), so this migration reshapes
-- them directly rather than writing backfill logic for rows that don't exist.
--
-- Every ADD/DROP/RENAME below is written defensively (IF [NOT] EXISTS, or a
-- guarded DO block for renames) because some developer databases already
-- have some of the "new" columns auto-added by dev's ddl-auto=update
-- alongside the old ones it never dropped - the same hybrid state this
-- migration exists to clean up. The guards are no-ops on a genuinely fresh
-- database; they only matter for reconciling that pre-existing drift.

-- ============================================================
-- instructor_profiles: add the name/bio/active fields the entity now owns
-- directly (previously assumed to live elsewhere); drop the unused
-- rate/availability fields that were never adopted by the entity.
-- ============================================================
ALTER TABLE instructor_profiles
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(20),
    ADD COLUMN IF NOT EXISTS bio TEXT,
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE instructor_profiles ALTER COLUMN first_name SET NOT NULL;
ALTER TABLE instructor_profiles ALTER COLUMN last_name SET NOT NULL;
ALTER TABLE instructor_profiles ALTER COLUMN specialization TYPE VARCHAR(200);
ALTER TABLE instructor_profiles ALTER COLUMN license_number SET NOT NULL;

ALTER TABLE instructor_profiles
    DROP COLUMN IF EXISTS hourly_rate,
    DROP COLUMN IF EXISTS availability,
    DROP COLUMN IF EXISTS availability_type;

CREATE INDEX IF NOT EXISTS idx_instructor_profiles_active ON instructor_profiles(active);

-- ============================================================
-- student_profiles: add the name/enrollment/status fields the entity now
-- owns; drop the address/license/medical fields that were never adopted.
-- ============================================================
ALTER TABLE student_profiles
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS enrollment_date DATE,
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(500);

ALTER TABLE student_profiles ALTER COLUMN first_name SET NOT NULL;
ALTER TABLE student_profiles ALTER COLUMN last_name SET NOT NULL;
ALTER TABLE student_profiles ALTER COLUMN enrollment_date SET NOT NULL;

ALTER TABLE student_profiles
    DROP COLUMN IF EXISTS address,
    DROP COLUMN IF EXISTS license_type,
    DROP COLUMN IF EXISTS license_number,
    DROP COLUMN IF EXISTS medical_cleared;

CREATE INDEX IF NOT EXISTS idx_student_profiles_status ON student_profiles(status);

-- ============================================================
-- driving_assessments: add result/duration_minutes; drop the unused
-- assessment_type/passed columns the entity replaced with "result".
-- ============================================================
ALTER TABLE driving_assessments
    ADD COLUMN IF NOT EXISTS result VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS duration_minutes INT;

ALTER TABLE driving_assessments ALTER COLUMN assessment_date SET NOT NULL;
ALTER TABLE driving_assessments ALTER COLUMN score SET NOT NULL;

ALTER TABLE driving_assessments
    DROP COLUMN IF EXISTS assessment_type,
    DROP COLUMN IF EXISTS passed;

-- ============================================================
-- learning_resources: was a direct course attachment; is now scoped to a
-- specific video lesson within the course, and typed as "type" not
-- "resource_type". Retarget the FK from courses to video_lessons.
-- ============================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='learning_resources' AND column_name='name') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='learning_resources' AND column_name='title') THEN
            ALTER TABLE learning_resources DROP COLUMN name;
        ELSE
            ALTER TABLE learning_resources RENAME COLUMN name TO title;
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='learning_resources' AND column_name='resource_type') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='learning_resources' AND column_name='type') THEN
            ALTER TABLE learning_resources DROP COLUMN resource_type;
        ELSE
            ALTER TABLE learning_resources RENAME COLUMN resource_type TO type;
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='learning_resources' AND column_name='course_id') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='learning_resources' AND column_name='lesson_id') THEN
            ALTER TABLE learning_resources DROP COLUMN course_id;
        ELSE
            ALTER TABLE learning_resources RENAME COLUMN course_id TO lesson_id;
        END IF;
    END IF;
END $$;

ALTER TABLE learning_resources ALTER COLUMN type SET NOT NULL;
ALTER TABLE learning_resources ALTER COLUMN file_url SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c
        JOIN pg_attribute a ON a.attnum = ANY(c.conkey) AND a.attrelid = c.conrelid
        WHERE c.conrelid = 'learning_resources'::regclass AND c.contype = 'f'
          AND c.confrelid = 'video_lessons'::regclass AND a.attname = 'lesson_id'
    ) THEN
        ALTER TABLE learning_resources
            ADD CONSTRAINT learning_resources_lesson_id_fkey
                FOREIGN KEY (lesson_id) REFERENCES video_lessons(id) ON DELETE CASCADE;
    END IF;
END $$;

DROP INDEX IF EXISTS idx_learning_resources_course_id;
CREATE INDEX IF NOT EXISTS idx_learning_resources_lesson_id ON learning_resources(lesson_id);

-- ============================================================
-- vehicles: rename to match entity fields; add color/status/gps_device_id;
-- drop the unused vin/transmission/mileage/last_serviced/is_active columns.
-- ============================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='vehicles' AND column_name='license_plate') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='vehicles' AND column_name='registration_number') THEN
            ALTER TABLE vehicles DROP COLUMN license_plate;
        ELSE
            ALTER TABLE vehicles RENAME COLUMN license_plate TO registration_number;
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='vehicles' AND column_name='year') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='vehicles' AND column_name='model_year') THEN
            ALTER TABLE vehicles DROP COLUMN year;
        ELSE
            ALTER TABLE vehicles RENAME COLUMN year TO model_year;
        END IF;
    END IF;
END $$;

ALTER TABLE vehicles
    ADD COLUMN IF NOT EXISTS color VARCHAR(30) NOT NULL DEFAULT 'UNSPECIFIED',
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN IF NOT EXISTS gps_device_id VARCHAR(100);

ALTER TABLE vehicles
    DROP COLUMN IF EXISTS vin,
    DROP COLUMN IF EXISTS transmission,
    DROP COLUMN IF EXISTS mileage,
    DROP COLUMN IF EXISTS last_serviced,
    DROP COLUMN IF EXISTS is_active;

CREATE INDEX IF NOT EXISTS idx_vehicles_status ON vehicles(status);

-- ============================================================
-- vehicle_locations: rename timestamp_loc -> recorded_at; add speed/heading;
-- drop the unused address column.
-- ============================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='vehicle_locations' AND column_name='timestamp_loc') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='vehicle_locations' AND column_name='recorded_at') THEN
            ALTER TABLE vehicle_locations DROP COLUMN timestamp_loc;
        ELSE
            ALTER TABLE vehicle_locations RENAME COLUMN timestamp_loc TO recorded_at;
        END IF;
    END IF;
END $$;

ALTER TABLE vehicle_locations
    ADD COLUMN IF NOT EXISTS speed DECIMAL(6, 2),
    ADD COLUMN IF NOT EXISTS heading DECIMAL(5, 2);

ALTER TABLE vehicle_locations ALTER COLUMN latitude SET NOT NULL;
ALTER TABLE vehicle_locations ALTER COLUMN longitude SET NOT NULL;
ALTER TABLE vehicle_locations ALTER COLUMN recorded_at SET NOT NULL;

ALTER TABLE vehicle_locations DROP COLUMN IF EXISTS address;

CREATE INDEX IF NOT EXISTS idx_vehicle_locations_recorded_at ON vehicle_locations(recorded_at);

-- ============================================================
-- live_sessions: was tied directly to a course; is now school-scoped with
-- its own scheduling fields instead of an explicit end time. Drop the
-- course relationship entirely (no longer modeled on the entity).
-- ============================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='live_sessions' AND column_name='session_name') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='live_sessions' AND column_name='title') THEN
            ALTER TABLE live_sessions DROP COLUMN session_name;
        ELSE
            ALTER TABLE live_sessions RENAME COLUMN session_name TO title;
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='live_sessions' AND column_name='start_time') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='live_sessions' AND column_name='scheduled_at') THEN
            ALTER TABLE live_sessions DROP COLUMN start_time;
        ELSE
            ALTER TABLE live_sessions RENAME COLUMN start_time TO scheduled_at;
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='live_sessions' AND column_name='room_url') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='live_sessions' AND column_name='meeting_url') THEN
            ALTER TABLE live_sessions DROP COLUMN room_url;
        ELSE
            ALTER TABLE live_sessions RENAME COLUMN room_url TO meeting_url;
        END IF;
    END IF;
END $$;

ALTER TABLE live_sessions
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS duration_minutes INT,
    ADD COLUMN IF NOT EXISTS max_participants INT,
    ADD COLUMN IF NOT EXISTS school_id BIGINT REFERENCES schools(id) ON DELETE CASCADE;

ALTER TABLE live_sessions ALTER COLUMN duration_minutes SET NOT NULL;
ALTER TABLE live_sessions ALTER COLUMN school_id SET NOT NULL;

ALTER TABLE live_sessions DROP CONSTRAINT IF EXISTS live_sessions_course_id_fkey;
ALTER TABLE live_sessions
    DROP COLUMN IF EXISTS end_time,
    DROP COLUMN IF EXISTS recording_url,
    DROP COLUMN IF EXISTS course_id;

CREATE INDEX IF NOT EXISTS idx_live_sessions_scheduled_at ON live_sessions(scheduled_at);

-- ============================================================
-- license_workflows: was tied to a course with a free-text stage/progress
-- model; is now course-independent with structured stage tracking. Drop
-- the course relationship entirely (no longer modeled on the entity).
-- ============================================================
ALTER TABLE license_workflows
    ADD COLUMN IF NOT EXISTS theory_progress_percent INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS road_training_hours INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS stage_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS approved_by_instructor_id BIGINT REFERENCES instructor_profiles(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS notes TEXT;

ALTER TABLE license_workflows ALTER COLUMN current_stage SET DEFAULT 'THEORY_LEARNING';
UPDATE license_workflows SET current_stage = 'THEORY_LEARNING' WHERE current_stage IS NULL;
ALTER TABLE license_workflows ALTER COLUMN current_stage SET NOT NULL;

ALTER TABLE license_workflows DROP CONSTRAINT IF EXISTS license_workflows_course_id_fkey;
ALTER TABLE license_workflows
    DROP COLUMN IF EXISTS course_id,
    DROP COLUMN IF EXISTS license_type,
    DROP COLUMN IF EXISTS stage_progress,
    DROP COLUMN IF EXISTS completed,
    DROP COLUMN IF EXISTS completion_date;

CREATE INDEX IF NOT EXISTS idx_license_workflows_current_stage ON license_workflows(current_stage);

-- ============================================================
-- notifications: was a simple read/unread message; is now a multi-channel
-- delivery record with its own status and failure tracking.
-- ============================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='notifications' AND column_name='title') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='notifications' AND column_name='subject') THEN
            ALTER TABLE notifications DROP COLUMN title;
        ELSE
            ALTER TABLE notifications RENAME COLUMN title TO subject;
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='notifications' AND column_name='message') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='notifications' AND column_name='body') THEN
            ALTER TABLE notifications DROP COLUMN message;
        ELSE
            ALTER TABLE notifications RENAME COLUMN message TO body;
        END IF;
    END IF;
END $$;

ALTER TABLE notifications ALTER COLUMN subject SET NOT NULL;

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS recipient_address VARCHAR(255),
    ADD COLUMN IF NOT EXISTS sent_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(500);

ALTER TABLE notifications
    DROP COLUMN IF EXISTS notification_type,
    DROP COLUMN IF EXISTS is_read,
    DROP COLUMN IF EXISTS read_at;

CREATE INDEX IF NOT EXISTS idx_notifications_channel ON notifications(channel);
CREATE INDEX IF NOT EXISTS idx_notifications_status ON notifications(status);

-- ============================================================
-- lesson_note_attachments: V6 never added updated_at. Every entity inherits
-- a NOT NULL updated_at from BaseEntity regardless of whether it also
-- declares its own @CreationTimestamp created_at (as this entity does), so
-- this was missed even though the table otherwise matches the entity.
-- ============================================================
ALTER TABLE lesson_note_attachments
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- ============================================================
-- lesson_question_status_history: same gap as above. This entity declares
-- neither createdAt nor updatedAt itself (both fully inherited from
-- BaseEntity), but V3 only ever added created_at.
-- ============================================================
ALTER TABLE lesson_question_status_history
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
