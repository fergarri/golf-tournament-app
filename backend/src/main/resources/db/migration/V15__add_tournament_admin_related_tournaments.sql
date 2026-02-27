-- Support multiple related tournaments for tournament_admin

CREATE TABLE tournament_admin_related_tournaments (
    tournament_admin_id BIGINT NOT NULL REFERENCES tournament_admins(id) ON DELETE CASCADE,
    tournament_id BIGINT NOT NULL REFERENCES tournaments(id) ON DELETE RESTRICT,
    PRIMARY KEY (tournament_admin_id, tournament_id),
    CONSTRAINT uq_tadmin_related_tournament UNIQUE (tournament_id)
);

CREATE INDEX idx_tadmin_related_admin_id
    ON tournament_admin_related_tournaments(tournament_admin_id);

CREATE INDEX idx_tadmin_related_tournament_id
    ON tournament_admin_related_tournaments(tournament_id);

-- Migrate legacy single relation data from tournament_admins.tournament_id
INSERT INTO tournament_admin_related_tournaments (tournament_admin_id, tournament_id)
SELECT id, tournament_id
FROM tournament_admins
WHERE tournament_id IS NOT NULL
ON CONFLICT DO NOTHING;

DROP INDEX IF EXISTS idx_tournament_admins_tournament_id;
ALTER TABLE tournament_admins DROP COLUMN IF EXISTS tournament_id;
