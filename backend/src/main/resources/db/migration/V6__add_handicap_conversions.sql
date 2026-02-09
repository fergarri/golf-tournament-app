-- Add handicap conversions table
CREATE TABLE handicap_conversions (
    id BIGSERIAL PRIMARY KEY,
    tee_id BIGINT NOT NULL REFERENCES course_tees(id),
    hcp_index_from NUMERIC(4,1) NOT NULL,  -- -5.0, -4.6, etc.
    hcp_index_to NUMERIC(4,1) NOT NULL,    -- -4.7, -3.7, etc.
    course_handicap INTEGER NOT NULL,          -- -9, -8, -7, etc.
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_handicap_conversion UNIQUE (tee_id, hcp_index_from, hcp_index_to)
);

CREATE INDEX idx_handicap_conversions_tee_range 
ON handicap_conversions(tee_id, hcp_index_from, hcp_index_to);
