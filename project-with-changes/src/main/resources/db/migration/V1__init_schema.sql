-- V1__init_schema.sql
-- Initial schema for Driving School Management Platform

-- Create roles table
CREATE TABLE IF NOT EXISTS roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create schools table
CREATE TABLE IF NOT EXISTS schools (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    address    VARCHAR(500) NOT NULL,
    phone      VARCHAR(20),
    email      VARCHAR(255),
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password       VARCHAR(255) NOT NULL,
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at  TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on users table
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_enabled ON users(enabled);

-- Create user_roles junction table
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Create student_profiles table
CREATE TABLE IF NOT EXISTS student_profiles (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    school_id       BIGINT NOT NULL REFERENCES schools(id) ON DELETE RESTRICT,
    date_of_birth   DATE,
    phone           VARCHAR(20),
    address         VARCHAR(500),
    license_type    VARCHAR(50),
    license_number  VARCHAR(50) UNIQUE,
    medical_cleared BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on student_profiles
CREATE INDEX IF NOT EXISTS idx_student_profiles_user_id ON student_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_student_profiles_school_id ON student_profiles(school_id);

-- Create instructor_profiles table
CREATE TABLE IF NOT EXISTS instructor_profiles (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    school_id         BIGINT NOT NULL REFERENCES schools(id) ON DELETE RESTRICT,
    license_number    VARCHAR(50) UNIQUE,
    years_experience  INT,
    specialization    VARCHAR(100),
    hourly_rate       DECIMAL(10, 2),
    availability      TEXT,
    availability_type VARCHAR(50),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on instructor_profiles
CREATE INDEX IF NOT EXISTS idx_instructor_profiles_user_id ON instructor_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_instructor_profiles_school_id ON instructor_profiles(school_id);

-- Create vehicles table
CREATE TABLE IF NOT EXISTS vehicles (
    id              BIGSERIAL PRIMARY KEY,
    school_id       BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    make            VARCHAR(100) NOT NULL,
    model           VARCHAR(100) NOT NULL,
    year            INT NOT NULL,
    license_plate   VARCHAR(50) NOT NULL UNIQUE,
    vin             VARCHAR(50) UNIQUE,
    transmission    VARCHAR(50),
    mileage         INT,
    last_serviced   DATE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on vehicles
CREATE INDEX IF NOT EXISTS idx_vehicles_school_id ON vehicles(school_id);
CREATE INDEX IF NOT EXISTS idx_vehicles_license_plate ON vehicles(license_plate);

-- Create vehicle_locations table
CREATE TABLE IF NOT EXISTS vehicle_locations (
    id              BIGSERIAL PRIMARY KEY,
    vehicle_id      BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    latitude        DECIMAL(10, 8),
    longitude       DECIMAL(11, 8),
    address         VARCHAR(500),
    timestamp_loc   TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on vehicle_locations
CREATE INDEX IF NOT EXISTS idx_vehicle_locations_vehicle_id ON vehicle_locations(vehicle_id);

-- Create courses table
CREATE TABLE IF NOT EXISTS courses (
    id           BIGSERIAL PRIMARY KEY,
    school_id    BIGINT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    duration_hours INT,
    price        DECIMAL(10, 2),
    license_type VARCHAR(50),
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on courses
CREATE INDEX IF NOT EXISTS idx_courses_school_id ON courses(school_id);

-- Create video_lessons table
CREATE TABLE IF NOT EXISTS video_lessons (
    id          BIGSERIAL PRIMARY KEY,
    course_id   BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title       VARCHAR(300) NOT NULL,
    description TEXT,
    duration_seconds INT,
    video_url   VARCHAR(500),
    order_index INT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on video_lessons
CREATE INDEX IF NOT EXISTS idx_video_lessons_course_id ON video_lessons(course_id);

-- Create learning_resources table
CREATE TABLE IF NOT EXISTS learning_resources (
    id          BIGSERIAL PRIMARY KEY,
    course_id   BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    name        VARCHAR(300) NOT NULL,
    resource_type VARCHAR(50),
    file_url    VARCHAR(500),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on learning_resources
CREATE INDEX IF NOT EXISTS idx_learning_resources_course_id ON learning_resources(course_id);

-- Create quizzes table
CREATE TABLE IF NOT EXISTS quizzes (
    id          BIGSERIAL PRIMARY KEY,
    course_id   BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title       VARCHAR(300) NOT NULL,
    description TEXT,
    pass_score  INT,
    duration_minutes INT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on quizzes
CREATE INDEX IF NOT EXISTS idx_quizzes_course_id ON quizzes(course_id);

-- Create quiz_questions table
CREATE TABLE IF NOT EXISTS quiz_questions (
    id          BIGSERIAL PRIMARY KEY,
    quiz_id     BIGINT NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    question_text TEXT NOT NULL,
    question_type VARCHAR(50),
    options     TEXT,
    correct_answer VARCHAR(500),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on quiz_questions
CREATE INDEX IF NOT EXISTS idx_quiz_questions_quiz_id ON quiz_questions(quiz_id);

-- Create quiz_submissions table
CREATE TABLE IF NOT EXISTS quiz_submissions (
    id          BIGSERIAL PRIMARY KEY,
    quiz_id     BIGINT NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    student_id  BIGINT NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
    score       INT,
    status      VARCHAR(50),
    submitted_at TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on quiz_submissions
CREATE INDEX IF NOT EXISTS idx_quiz_submissions_quiz_id ON quiz_submissions(quiz_id);
CREATE INDEX IF NOT EXISTS idx_quiz_submissions_student_id ON quiz_submissions(student_id);

-- Create bookings table
CREATE TABLE IF NOT EXISTS bookings (
    id              BIGSERIAL PRIMARY KEY,
    student_id      BIGINT NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
    instructor_id   BIGINT NOT NULL REFERENCES instructor_profiles(id) ON DELETE CASCADE,
    vehicle_id      BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE SET NULL,
    start_time      TIMESTAMP NOT NULL,
    end_time        TIMESTAMP NOT NULL,
    status          VARCHAR(50) NOT NULL,
    lesson_type     VARCHAR(50),
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on bookings
CREATE INDEX IF NOT EXISTS idx_bookings_student_id ON bookings(student_id);
CREATE INDEX IF NOT EXISTS idx_bookings_instructor_id ON bookings(instructor_id);
CREATE INDEX IF NOT EXISTS idx_bookings_vehicle_id ON bookings(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings(status);
CREATE INDEX IF NOT EXISTS idx_bookings_start_time ON bookings(start_time);

-- Create live_sessions table
CREATE TABLE IF NOT EXISTS live_sessions (
    id              BIGSERIAL PRIMARY KEY,
    instructor_id   BIGINT NOT NULL REFERENCES instructor_profiles(id) ON DELETE CASCADE,
    course_id       BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    session_name    VARCHAR(300) NOT NULL,
    start_time      TIMESTAMP NOT NULL,
    end_time        TIMESTAMP,
    room_url        VARCHAR(500),
    status          VARCHAR(50) NOT NULL,
    recording_url   VARCHAR(500),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on live_sessions
CREATE INDEX IF NOT EXISTS idx_live_sessions_instructor_id ON live_sessions(instructor_id);
CREATE INDEX IF NOT EXISTS idx_live_sessions_course_id ON live_sessions(course_id);
CREATE INDEX IF NOT EXISTS idx_live_sessions_status ON live_sessions(status);

-- Create attendances table
CREATE TABLE IF NOT EXISTS attendances (
    id              BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,
    student_id      BIGINT NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
    checked_in_at       TIMESTAMP,
    notes               VARCHAR(500),
    status              VARCHAR(30),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on attendances
CREATE INDEX IF NOT EXISTS idx_attendances_session_id ON attendances(session_id);
CREATE INDEX IF NOT EXISTS idx_attendances_student_id ON attendances(student_id);

-- Create payments table
CREATE TABLE IF NOT EXISTS payments (
    id              BIGSERIAL PRIMARY KEY,
    student_id      BIGINT NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
    amount          DECIMAL(10, 2) NOT NULL,
    payment_type    VARCHAR(50) NOT NULL,
    reference_type  VARCHAR(50),
    reference_id    BIGINT,
    status          VARCHAR(50) NOT NULL,
    transaction_id  VARCHAR(100) UNIQUE,
    payment_method  VARCHAR(50),
    payment_date    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on payments
CREATE INDEX IF NOT EXISTS idx_payments_student_id ON payments(student_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payments_payment_date ON payments(payment_date);

-- Create license_workflows table
CREATE TABLE IF NOT EXISTS license_workflows (
    id              BIGSERIAL PRIMARY KEY,
    student_id      BIGINT NOT NULL UNIQUE REFERENCES student_profiles(id) ON DELETE CASCADE,
    course_id       BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    license_type    VARCHAR(50),
    current_stage   VARCHAR(100),
    stage_progress  INT,
    completed       BOOLEAN DEFAULT FALSE,
    completion_date TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on license_workflows
CREATE INDEX IF NOT EXISTS idx_license_workflows_student_id ON license_workflows(student_id);
CREATE INDEX IF NOT EXISTS idx_license_workflows_course_id ON license_workflows(course_id);

-- Create driving_assessments table
CREATE TABLE IF NOT EXISTS driving_assessments (
    id              BIGSERIAL PRIMARY KEY,
    student_id      BIGINT NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
    instructor_id   BIGINT NOT NULL REFERENCES instructor_profiles(id) ON DELETE CASCADE,
    assessment_type VARCHAR(100),
    score           INT,
    feedback        TEXT,
    passed          BOOLEAN DEFAULT FALSE,
    assessment_date TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on driving_assessments
CREATE INDEX IF NOT EXISTS idx_driving_assessments_student_id ON driving_assessments(student_id);
CREATE INDEX IF NOT EXISTS idx_driving_assessments_instructor_id ON driving_assessments(instructor_id);

-- Create notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(300),
    message         TEXT NOT NULL,
    notification_type VARCHAR(50),
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    read_at         TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on notifications
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_is_read ON notifications(is_read);

-- Insert default roles (with conflict handling for idempotency)
INSERT INTO roles (name, description, created_at, updated_at)
SELECT 'ADMIN', 'Administrator with full system access', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');

INSERT INTO roles (name, description, created_at, updated_at)
SELECT 'INSTRUCTOR', 'Driving instructor', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'INSTRUCTOR');

INSERT INTO roles (name, description, created_at, updated_at)
SELECT 'STUDENT', 'Student learner', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'STUDENT');
