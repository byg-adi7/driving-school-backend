CREATE TABLE lesson_notes (
    id BIGSERIAL PRIMARY KEY,
    live_session_id BIGINT NOT NULL UNIQUE,
    instructor_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    lesson_summary VARCHAR(1000),
    strengths VARCHAR(1500),
    weaknesses VARCHAR(1500),
    recommendations VARCHAR(1500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    updated_by_id BIGINT,
    CONSTRAINT fk_lesson_notes_instructor FOREIGN KEY (instructor_id) REFERENCES instructor_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_lesson_notes_student FOREIGN KEY (student_id) REFERENCES student_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_lesson_notes_updated_by FOREIGN KEY (updated_by_id) REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_lesson_notes_instructor ON lesson_notes(instructor_id);
CREATE INDEX idx_lesson_notes_student ON lesson_notes(student_id);
CREATE INDEX idx_lesson_notes_live_session ON lesson_notes(live_session_id);
CREATE INDEX idx_lesson_notes_created_at ON lesson_notes(created_at);
