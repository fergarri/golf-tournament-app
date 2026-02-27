CREATE TABLE tournament_admin_playoff_results (
    id BIGSERIAL PRIMARY KEY,
    tournament_admin_id BIGINT NOT NULL REFERENCES tournament_admins(id) ON DELETE CASCADE,
    player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE RESTRICT,
    total_points INTEGER NOT NULL DEFAULT 0,
    position INTEGER NOT NULL,
    qualified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tadmin_playoff_result UNIQUE (tournament_admin_id, player_id)
);

CREATE INDEX idx_tadmin_playoff_results_admin_id
    ON tournament_admin_playoff_results(tournament_admin_id);

CREATE INDEX idx_tadmin_playoff_results_position
    ON tournament_admin_playoff_results(tournament_admin_id, position);
