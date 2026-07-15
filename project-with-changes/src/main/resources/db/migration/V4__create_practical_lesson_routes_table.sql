CREATE TABLE practical_lesson_routes (
    id BIGSERIAL PRIMARY KEY,
    live_session_id BIGINT NOT NULL UNIQUE,
    instructor_id BIGINT NOT NULL,
    start_location VARCHAR(500) NOT NULL,
    destination_location VARCHAR(500) NOT NULL,
    start_lat DOUBLE PRECISION NOT NULL,
    start_lon DOUBLE PRECISION NOT NULL,
    dest_lat DOUBLE PRECISION NOT NULL,
    dest_lon DOUBLE PRECISION NOT NULL,
    distance_meters BIGINT NOT NULL,
    duration_seconds BIGINT NOT NULL,
    route_geometry TEXT NOT NULL,
    external_route_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_route_instructor FOREIGN KEY (instructor_id) REFERENCES instructor_profiles(id) ON DELETE CASCADE
);
CREATE INDEX idx_route_instructor ON practical_lesson_routes(instructor_id);
CREATE INDEX idx_route_live_session ON practical_lesson_routes(live_session_id);
CREATE INDEX idx_route_created_at ON practical_lesson_routes(created_at);
