-- Premios habilitados por torneo
CREATE TABLE tournament_prizes (
    id BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    prize_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tournament_prize UNIQUE (tournament_id, prize_type)
);

CREATE INDEX idx_tournament_prizes_tournament_id ON tournament_prizes(tournament_id);

-- Ganadores de premios
CREATE TABLE tournament_prize_winners (
    id BIGSERIAL PRIMARY KEY,
    tournament_prize_id BIGINT NOT NULL REFERENCES tournament_prizes(id) ON DELETE CASCADE,
    inscription_id BIGINT NOT NULL REFERENCES tournament_inscriptions(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tournament_prize_winner UNIQUE (tournament_prize_id)
);

CREATE INDEX idx_tournament_prize_winners_prize_id ON tournament_prize_winners(tournament_prize_id);
CREATE INDEX idx_tournament_prize_winners_inscription_id ON tournament_prize_winners(inscription_id);
