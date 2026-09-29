-- ============================================================
-- Direct messaging between a student and an instructor of the same school, and
-- one-way school-wide announcements from an instructor to that school's students.
--
-- Every foreign key cascades on delete, matching V15: the bootstrap admin's
-- school/admin cascade-delete removes a school and, via its profiles, its users -
-- a RESTRICT (or clause-less) FK here would block that delete, exactly the class
-- of bug V15 had to fix for the older tables.
-- ============================================================

-- One conversation per (student, instructor) pair, reused for every message
-- between them. school_id is denormalised from the two profiles (which must
-- share it) so the school cascade doesn't depend on the profile cascades alone.
CREATE TABLE IF NOT EXISTS conversations (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    instructor_id BIGINT NOT NULL,
    last_message_at TIMESTAMP,
    -- first ~200 characters of the latest message, for an inbox list without loading threads
    last_message_preview VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_conversations_student_instructor UNIQUE (student_id, instructor_id),
    CONSTRAINT fk_conversations_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_conversations_student FOREIGN KEY (student_id) REFERENCES student_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_conversations_instructor FOREIGN KEY (instructor_id) REFERENCES instructor_profiles(id) ON DELETE CASCADE
);
-- (student_id, ...) lookups are already served by the unique constraint's index.
CREATE INDEX IF NOT EXISTS idx_conversations_instructor ON conversations (instructor_id);

CREATE TABLE IF NOT EXISTS messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    body TEXT NOT NULL,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_messages_conversation_created ON messages (conversation_id, created_at);

CREATE TABLE IF NOT EXISTS announcements (
    id BIGSERIAL PRIMARY KEY,
    school_id BIGINT NOT NULL,
    instructor_id BIGINT NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_announcements_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
    CONSTRAINT fk_announcements_instructor FOREIGN KEY (instructor_id) REFERENCES instructor_profiles(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_announcements_school_created ON announcements (school_id, created_at);
