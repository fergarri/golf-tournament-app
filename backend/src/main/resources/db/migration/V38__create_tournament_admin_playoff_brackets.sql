CREATE TABLE tournament_admin_playoff_brackets (
    id BIGSERIAL PRIMARY KEY,
    tournament_admin_id BIGINT NOT NULL REFERENCES tournament_admins(id) ON DELETE CASCADE,
    score_type VARCHAR(20) NOT NULL,
    size INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_playoff_bracket_admin_score_type UNIQUE (tournament_admin_id, score_type)
);

CREATE INDEX idx_playoff_brackets_admin_id
    ON tournament_admin_playoff_brackets(tournament_admin_id);

CREATE TABLE tournament_admin_playoff_bracket_slots (
    id BIGSERIAL PRIMARY KEY,
    bracket_id BIGINT NOT NULL REFERENCES tournament_admin_playoff_brackets(id) ON DELETE CASCADE,
    round_number INTEGER NOT NULL,
    slot_index INTEGER NOT NULL,
    player_id BIGINT REFERENCES players(id) ON DELETE RESTRICT,
    is_winner BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_playoff_bracket_slot UNIQUE (bracket_id, round_number, slot_index)
);

CREATE INDEX idx_playoff_bracket_slots_bracket_id
    ON tournament_admin_playoff_bracket_slots(bracket_id);
