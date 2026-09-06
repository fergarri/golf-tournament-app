-- Match Play para los partidos de la llave de Playoff (ver docs/diseno-match-play-playoff.md)

CREATE TABLE tournament_admin_playoff_round_sessions (
    id BIGSERIAL PRIMARY KEY,
    bracket_id BIGINT NOT NULL REFERENCES tournament_admin_playoff_brackets(id) ON DELETE CASCADE,
    round_number INTEGER NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    tee_masculino_id BIGINT REFERENCES course_tees(id),
    tee_femenino_id BIGINT REFERENCES course_tees(id),
    cantidad_hoyos_juego INTEGER NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_playoff_round_session UNIQUE (bracket_id, round_number)
);

CREATE INDEX idx_playoff_round_sessions_bracket_id
    ON tournament_admin_playoff_round_sessions(bracket_id);

CREATE TABLE tournament_admin_playoff_matches (
    id BIGSERIAL PRIMARY KEY,
    round_session_id BIGINT NOT NULL REFERENCES tournament_admin_playoff_round_sessions(id) ON DELETE CASCADE,
    top_slot_id BIGINT NOT NULL REFERENCES tournament_admin_playoff_bracket_slots(id) ON DELETE RESTRICT,
    bottom_slot_id BIGINT NOT NULL REFERENCES tournament_admin_playoff_bracket_slots(id) ON DELETE RESTRICT,
    player_a_id BIGINT NOT NULL REFERENCES players(id) ON DELETE RESTRICT,
    player_b_id BIGINT NOT NULL REFERENCES players(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    winner_player_id BIGINT REFERENCES players(id),
    winner_slot_id BIGINT REFERENCES tournament_admin_playoff_bracket_slots(id),
    result_summary VARCHAR(50),
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_playoff_match_top_slot UNIQUE (round_session_id, top_slot_id)
);

CREATE INDEX idx_playoff_matches_round_session_id
    ON tournament_admin_playoff_matches(round_session_id);

CREATE TABLE tournament_admin_playoff_match_cards (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL REFERENCES tournament_admin_playoff_matches(id) ON DELETE CASCADE,
    player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE RESTRICT,
    tee_id BIGINT REFERENCES course_tees(id),
    handicap_course INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    delivered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_playoff_match_card UNIQUE (match_id, player_id)
);

CREATE INDEX idx_playoff_match_cards_match_id
    ON tournament_admin_playoff_match_cards(match_id);

CREATE TABLE tournament_admin_playoff_match_hole_scores (
    id BIGSERIAL PRIMARY KEY,
    card_id BIGINT NOT NULL REFERENCES tournament_admin_playoff_match_cards(id) ON DELETE CASCADE,
    hole_sequence INTEGER NOT NULL,
    hole_id BIGINT NOT NULL REFERENCES holes(id) ON DELETE RESTRICT,
    golpes_propio INTEGER,
    golpes_rival INTEGER,
    validado BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_playoff_match_hole_score UNIQUE (card_id, hole_sequence)
);

CREATE INDEX idx_playoff_match_hole_scores_card_id
    ON tournament_admin_playoff_match_hole_scores(card_id);
