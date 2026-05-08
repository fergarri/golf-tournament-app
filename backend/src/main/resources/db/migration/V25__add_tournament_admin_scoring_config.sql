-- Configuración de puntuación por torneo administrativo
CREATE TABLE tournament_admin_scoring_config (
    id BIGSERIAL PRIMARY KEY,
    tournament_admin_id BIGINT NOT NULL REFERENCES tournament_admins(id) ON DELETE CASCADE,
    birdie_points INT NOT NULL DEFAULT 1,
    eagle_points INT NOT NULL DEFAULT 5,
    ace_points INT NOT NULL DEFAULT 10,
    participation_points INT NOT NULL DEFAULT 1,
    remaining_positions_points INT NOT NULL DEFAULT 0,
    qualified_playoff_positions INT NOT NULL DEFAULT 8,
    tie_break_mode VARCHAR(50) NOT NULL DEFAULT 'NETO_HCP_HOLE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tournament_admin_scoring_config UNIQUE (tournament_admin_id)
);

CREATE INDEX idx_tournament_admin_scoring_config_admin_id ON tournament_admin_scoring_config(tournament_admin_id);

-- Puntos por posición (tabla dinámica: N posiciones con sus puntos)
CREATE TABLE tournament_admin_scoring_position_points (
    id BIGSERIAL PRIMARY KEY,
    scoring_config_id BIGINT NOT NULL REFERENCES tournament_admin_scoring_config(id) ON DELETE CASCADE,
    position INT NOT NULL,
    points INT NOT NULL,
    CONSTRAINT uq_scoring_config_position UNIQUE (scoring_config_id, position)
);

CREATE INDEX idx_scoring_position_points_config_id ON tournament_admin_scoring_position_points(scoring_config_id);

-- Seed: insertar configuración Frutales actual para todos los TournamentAdmin que ya tienen torneos FRUTALES
INSERT INTO tournament_admin_scoring_config
    (tournament_admin_id, birdie_points, eagle_points, ace_points,
     participation_points, remaining_positions_points,
     qualified_playoff_positions, tie_break_mode)
SELECT DISTINCT ta.id, 1, 5, 10, 1, 0, 8, 'NETO_HCP_HOLE'
FROM tournament_admins ta
JOIN tournament_admin_related_tournaments tart ON tart.tournament_admin_id = ta.id
JOIN tournaments t ON t.id = tart.tournament_id
WHERE t.tipo = 'FRUTALES';

-- Seed: insertar puntos por posición actuales (1→12, 2→10, 3→8, 4→6, 5→4, 6→2) para esos TournamentAdmin
INSERT INTO tournament_admin_scoring_position_points (scoring_config_id, position, points)
SELECT sc.id, pos.position, pos.points
FROM tournament_admin_scoring_config sc
CROSS JOIN (VALUES (1,12),(2,10),(3,8),(4,6),(5,4),(6,2)) AS pos(position, points);
