-- Eliminar la constraint antigua de stage_scores (nombre real definido en V16)
ALTER TABLE tournament_admin_stage_scores
    DROP CONSTRAINT IF EXISTS uq_tadmin_stage_player;

-- Eliminar la constraint antigua de playoff_results (nombre real definido en V17)
ALTER TABLE tournament_admin_playoff_results
    DROP CONSTRAINT IF EXISTS uq_tadmin_playoff_result;
