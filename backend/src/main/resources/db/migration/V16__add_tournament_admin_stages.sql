-- Stages for FRUTALES tournament administration

CREATE TABLE tournament_admin_stages (
    id BIGSERIAL PRIMARY KEY,
    tournament_admin_id BIGINT NOT NULL REFERENCES tournament_admins(id) ON DELETE CASCADE,
    nombre VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tadmin_stages_admin_id
    ON tournament_admin_stages(tournament_admin_id);

CREATE TABLE tournament_admin_stage_tournaments (
    stage_id BIGINT NOT NULL REFERENCES tournament_admin_stages(id) ON DELETE CASCADE,
    tournament_id BIGINT NOT NULL REFERENCES tournaments(id) ON DELETE RESTRICT,
    PRIMARY KEY (stage_id, tournament_id),
    CONSTRAINT uq_tadmin_stage_tournament UNIQUE (tournament_id)
);

CREATE INDEX idx_tadmin_stage_tournaments_stage_id
    ON tournament_admin_stage_tournaments(stage_id);

CREATE INDEX idx_tadmin_stage_tournaments_tournament_id
    ON tournament_admin_stage_tournaments(tournament_id);

CREATE TABLE tournament_admin_stage_scores (
    id BIGSERIAL PRIMARY KEY,
    stage_id BIGINT NOT NULL REFERENCES tournament_admin_stages(id) ON DELETE CASCADE,
    player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE RESTRICT,
    total_points INTEGER NOT NULL DEFAULT 0,
    position INTEGER,
    tie_break_handicap_index NUMERIC(4,1),
    last_tournament_score_neto NUMERIC(10,2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tadmin_stage_player UNIQUE (stage_id, player_id)
);

CREATE INDEX idx_tadmin_stage_scores_stage_id
    ON tournament_admin_stage_scores(stage_id);
