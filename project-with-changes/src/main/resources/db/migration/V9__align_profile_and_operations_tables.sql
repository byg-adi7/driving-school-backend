-- Reconciles instructor_profiles/student_profiles/driving_assessments/
-- learning_resources/vehicles/vehicle_locations/live_sessions/license_workflows/
-- notifications with their current JPA entities. Like V8, these tables were
-- created in V1 for an earlier design and were never migrated when the
-- entities were redesigned; dev's ddl-auto=update silently patched each
-- developer's own database, so this only ever surfaced against a genuinely
-- fresh database (ddl-auto=validate, used in prod). Confirmed no production
-- data exists in any of these tables (pre-launch), so this migration reshapes
-- them directly rather than writing backfill logic for rows that don't exist.

-- ============================================================
-- instructor_profiles: add the name/bio/active fields the entity now owns
-- directly (previously assumed to live elsewhere); drop the unused
-- rate/availability fields that were never adopted by the entity.
-- ============================================================
ALTER TABLE instructor_profiles
    ADD COLUMN first_name VARCHAR(100) NOT NULL,
    ADD COLUMN last_name VARCHAR(100) NOT NULL,
    ADD COLUMN phone VARCHAR(20),
    ADD COLUMN bio TEXT,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE instructor_profiles ALTER COLUMN specialization TYPE VARCHAR(200);
ALTER TABLE instructor_profiles ALTER COLUMN license_number SET NOT NULL;

ALTER TABLE instructor_profiles
    DROP COLUMN hourly_rate,
    DROP COLUMN availability,
    DROP COLUMN availability_type;

CREATE INDEX IF NOT EXISTS idx_instructor_profiles_active ON instructor_profiles(active);

-- ============================================================
-- student_profiles: add the name/enrollment/status fields the entity now
-- owns; drop the address/license/medical fields that were never adopted.
-- ============================================================
ALTER TABLE student_profiles
    ADD COLUMN first_name VARCHAR(100) NOT NULL,
    ADD COLUMN last_name VARCHAR(100) NOT NULL,
    ADD COLUMN enrollment_date DATE NOT NULL,
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN profile_image_url VARCHAR(500);

ALTER TABLE student_profiles
    DROP COLUMN address,
    DROP COLUMN license_type,
    DROP COLUMN license_number,
    DROP COLUMN medical_cleared;

CREATE INDEX IF NOT EXISTS idx_student_profiles_status ON student_profiles(status);

-- ============================================================
-- driving_assessments: add result/duration_minutes; drop the unused
-- assessment_type/passed columns the entity replaced with "result".
-- ============================================================
ALTER TABLE driving_assessments
    ADD COLUMN result VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN duration_minutes INT;

ALTER TABLE driving_assessments ALTER COLUMN assessment_date SET NOT NULL;
ALTER TABLE driving_assessments ALTER COLUMN score SET NOT NULL;

ALTER TABLE driving_assessments
    DROP COLUMN assessment_type,
    DROP COLUMN passed;

-- ============================================================
-- learning_resources: was a direct course attachment; is now scoped to a
-- specific video lesson within the course, and typed as "type" not
-- "resource_type". Retarget the FK from courses to video_lessons.
-- ============================================================
ALTER TABLE learning_resources RENAME COLUMN name TO title;
ALTER TABLE learning_resources RENAME COLUMN resource_type TO type;
ALTER TABLE learning_resources RENAME COLUMN course_id TO lesson_id;

ALTER TABLE learning_resources ALTER COLUMN type SET NOT NULL;
ALTER TABLE learning_resources ALTER COLUMN file_url SET NOT NULL;

ALTER TABLE learning_resources DROP CONSTRAINT learning_resources_course_id_fkey;
ALTER TABLE learning_resources
    ADD CONSTRAINT learning_resources_lesson_id_fkey
        FOREIGN KEY (lesson_id) REFERENCES video_lessons(id) ON DELETE CASCADE;

DROP INDEX IF EXISTS idx_learning_resources_course_id;
CREATE INDEX IF NOT EXISTS idx_learning_resources_lesson_id ON learning_resources(lesson_id);

-- ============================================================
-- vehicles: rename to match entity fields; add color/status/gps_device_id;
-- drop the unused vin/transmission/mileage/last_serviced/is_active columns.
-- ============================================================
ALTER TABLE vehicles RENAME COLUMN license_plate TO registration_number;
ALTER TABLE vehicles RENAME COLUMN year TO model_year;

ALTER TABLE vehicles
    ADD COLUMN color VARCHAR(30) NOT NULL DEFAULT 'UNSPECIFIED',
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN gps_device_id VARCHAR(100);

ALTER TABLE vehicles
    DROP COLUMN vin,
    DROP COLUMN transmission,
    DROP COLUMN mileage,
    DROP COLUMN last_serviced,
    DROP COLUMN is_active;

CREATE INDEX IF NOT EXISTS idx_vehicles_status ON vehicles(status);

-- ============================================================
-- vehicle_locations: rename timestamp_loc -> recorded_at; add speed/heading;
-- drop the unused address column.
-- ============================================================
ALTER TABLE vehicle_locations RENAME COLUMN timestamp_loc TO recorded_at;

ALTER TABLE vehicle_locations
    ADD COLUMN speed DECIMAL(6, 2),
    ADD COLUMN heading DECIMAL(5, 2);

ALTER TABLE vehicle_locations ALTER COLUMN latitude SET NOT NULL;
ALTER TABLE vehicle_locations ALTER COLUMN longitude SET NOT NULL;
ALTER TABLE vehicle_locations ALTER COLUMN recorded_at SET NOT NULL;

ALTER TABLE vehicle_locations DROP COLUMN address;

CREATE INDEX IF NOT EXISTS idx_vehicle_locations_recorded_at ON vehicle_locations(recorded_at);

-- ============================================================
-- live_sessions: was tied directly to a course; is now school-scoped with
-- its own scheduling fields instead of an explicit end time. Drop the
-- course relationship entirely (no longer modeled on the entity).
-- ============================================================
ALTER TABLE live_sessions RENAME COLUMN session_name TO title;
ALTER TABLE live_sessions RENAME COLUMN start_time TO scheduled_at;
ALTER TABLE live_sessions RENAME COLUMN room_url TO meeting_url;

ALTER TABLE live_sessions
    ADD COLUMN description TEXT,
    ADD COLUMN duration_minutes INT NOT NULL,
    ADD COLUMN max_participants INT,
    ADD COLUMN school_id BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE;

ALTER TABLE live_sessions DROP CONSTRAINT live_sessions_course_id_fkey;
ALTER TABLE live_sessions
    DROP COLUMN end_time,
    DROP COLUMN recording_url,
    DROP COLUMN course_id;

CREATE INDEX IF NOT EXISTS idx_live_sessions_scheduled_at ON live_sessions(scheduled_at);

-- ============================================================
-- license_workflows: was tied to a course with a free-text stage/progress
-- model; is now course-independent with structured stage tracking. Drop
-- the course relationship entirely (no longer modeled on the entity).
-- ============================================================
ALTER TABLE license_workflows
    ADD COLUMN theory_progress_percent INT NOT NULL DEFAULT 0,
    ADD COLUMN road_training_hours INT NOT NULL DEFAULT 0,
    ADD COLUMN stage_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN approved_by_instructor_id BIGINT REFERENCES instructor_profiles(id) ON DELETE SET NULL,
    ADD COLUMN notes TEXT;

ALTER TABLE license_workflows ALTER COLUMN current_stage SET DEFAULT 'THEORY_LEARNING';
UPDATE license_workflows SET current_stage = 'THEORY_LEARNING' WHERE current_stage IS NULL;
ALTER TABLE license_workflows ALTER COLUMN current_stage SET NOT NULL;

ALTER TABLE license_workflows DROP CONSTRAINT license_workflows_course_id_fkey;
ALTER TABLE license_workflows
    DROP COLUMN course_id,
    DROP COLUMN license_type,
    DROP COLUMN stage_progress,
    DROP COLUMN completed,
    DROP COLUMN completion_date;

CREATE INDEX IF NOT EXISTS idx_license_workflows_current_stage ON license_workflows(current_stage);

-- ============================================================
-- notifications: was a simple read/unread message; is now a multi-channel
-- delivery record with its own status and failure tracking.
-- ============================================================
ALTER TABLE notifications RENAME COLUMN title TO subject;
ALTER TABLE notifications RENAME COLUMN message TO body;

ALTER TABLE notifications ALTER COLUMN subject SET NOT NULL;

ALTER TABLE notifications
    ADD COLUMN channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN recipient_address VARCHAR(255),
    ADD COLUMN sent_at TIMESTAMP,
    ADD COLUMN failure_reason VARCHAR(500);

ALTER TABLE notifications
    DROP COLUMN notification_type,
    DROP COLUMN is_read,
    DROP COLUMN read_at;

CREATE INDEX IF NOT EXISTS idx_notifications_channel ON notifications(channel);
CREATE INDEX IF NOT EXISTS idx_notifications_status ON notifications(status);

-- ============================================================
-- lesson_note_attachments: V6 never added updated_at. Every entity inherits
-- a NOT NULL updated_at from BaseEntity regardless of whether it also
-- declares its own @CreationTimestamp created_at (as this entity does), so
-- this was missed even though the table otherwise matches the entity.
-- ============================================================
ALTER TABLE lesson_note_attachments
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- ============================================================
-- lesson_question_status_history: same gap as above. This entity declares
-- neither createdAt nor updatedAt itself (both fully inherited from
-- BaseEntity), but V3 only ever added created_at.
-- ============================================================
ALTER TABLE lesson_question_status_history
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
