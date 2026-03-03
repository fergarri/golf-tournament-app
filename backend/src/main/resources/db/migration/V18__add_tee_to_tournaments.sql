ALTER TABLE tournaments
    ADD COLUMN tee_id BIGINT NULL REFERENCES course_tees(id);

CREATE INDEX idx_tournaments_tee_id ON tournaments(tee_id);
