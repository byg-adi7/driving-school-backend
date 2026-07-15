CREATE TYPE question_status AS ENUM ('PENDING', 'IN_PROGRESS', 'ANSWERED', 'CLOSED');

CREATE TABLE lesson_question_submissions (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    instructor_id BIGINT,
    subject VARCHAR(255) NOT NULL,
    question_body VARCHAR(3000) NOT NULL,
    response VARCHAR(3000),
    status question_status NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    responded_by_id BIGINT,
    responded_at TIMESTAMP,
    CONSTRAINT fk_question_student FOREIGN KEY (student_id) REFERENCES student_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_question_instructor FOREIGN KEY (instructor_id) REFERENCES instructor_profiles(id) ON DELETE SET NULL,
    CONSTRAINT fk_question_responded_by FOREIGN KEY (responded_by_id) REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_question_student ON lesson_question_submissions(student_id);
CREATE INDEX idx_question_instructor ON lesson_question_submissions(instructor_id);
CREATE INDEX idx_question_status ON lesson_question_submissions(status);
CREATE INDEX idx_question_created_at ON lesson_question_submissions(created_at);

CREATE TABLE lesson_question_status_history (
    id BIGSERIAL PRIMARY KEY,
    question_submission_id BIGINT NOT NULL,
    previous_status question_status,
    new_status question_status NOT NULL,
    changed_by_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    change_reason VARCHAR(500),
    CONSTRAINT fk_status_history_question FOREIGN KEY (question_submission_id) REFERENCES lesson_question_submissions(id) ON DELETE CASCADE,
    CONSTRAINT fk_status_history_changed_by FOREIGN KEY (changed_by_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_status_history_question ON lesson_question_status_history(question_submission_id);
CREATE INDEX idx_status_history_changed_by ON lesson_question_status_history(changed_by_id);
CREATE INDEX idx_status_history_created_at ON lesson_question_status_history(created_at);
