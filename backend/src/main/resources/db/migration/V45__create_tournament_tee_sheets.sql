-- Salidas de un torneo particular.
-- published = false es el borrador que se edita.
-- published = true es la última grilla confirmada, que sigue en la vista pública
-- mientras el borrador se vuelve a editar.

CREATE TABLE tournament_tee_sheets (
    id BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT NOT NULL UNIQUE REFERENCES tournaments(id) ON DELETE CASCADE,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tournament_tee_sheets_tournament_id
    ON tournament_tee_sheets(tournament_id);

CREATE TABLE tournament_tee_sheet_groups (
    id BIGSERIAL PRIMARY KEY,
    tee_sheet_id BIGINT NOT NULL REFERENCES tournament_tee_sheets(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    hole_number INTEGER,
    tee_time TIME,
    player_1_id BIGINT REFERENCES players(id) ON DELETE SET NULL,
    player_2_id BIGINT REFERENCES players(id) ON DELETE SET NULL,
    player_3_id BIGINT REFERENCES players(id) ON DELETE SET NULL,
    player_4_id BIGINT REFERENCES players(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tee_sheet_group_order UNIQUE (tee_sheet_id, published, sort_order)
);

CREATE INDEX idx_tee_sheet_groups_sheet_id
    ON tournament_tee_sheet_groups(tee_sheet_id);
