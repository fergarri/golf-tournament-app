-- 1. Renombrar tabla frutales_scores a tournament_scores
ALTER TABLE frutales_scores RENAME TO tournament_scores;

-- 2. Renombrar índice existente
ALTER INDEX idx_frutales_scores_tournament RENAME TO idx_tournament_scores_tournament;

-- 3. Renombrar la constraint unique existente (por defecto se llama frutales_scores_tournament_id_scorecard_id_key)
ALTER TABLE tournament_scores RENAME CONSTRAINT frutales_scores_tournament_id_scorecard_id_key
    TO uq_tournament_scores_tournament_scorecard_score_type;

-- 4. Agregar columna score_type (GLOBAL = Frutales, CATEGORY = Clásico por categoría, SCRATCH = Clásico scratch)
ALTER TABLE tournament_scores
    ADD COLUMN score_type VARCHAR(20) NOT NULL DEFAULT 'GLOBAL';

-- 5. Agregar columna category_id (FK a tournament_categories, null para GLOBAL y SCRATCH)
ALTER TABLE tournament_scores
    ADD COLUMN category_id BIGINT NULL REFERENCES tournament_categories(id) ON DELETE SET NULL;

-- 6. Actualizar la unique constraint para incluir score_type
--    (primero eliminamos la renombrada y creamos una nueva)
ALTER TABLE tournament_scores DROP CONSTRAINT uq_tournament_scores_tournament_scorecard_score_type;
ALTER TABLE tournament_scores
    ADD CONSTRAINT uq_tournament_scores_tournament_scorecard_score_type
    UNIQUE (tournament_id, scorecard_id, score_type);

-- 7. Agregar score_type a tournament_admin_stage_scores (HCP = por categoría/neto, SCRATCH = por gross)
ALTER TABLE tournament_admin_stage_scores
    ADD COLUMN score_type VARCHAR(20) NOT NULL DEFAULT 'HCP';

-- 8. Actualizar la unique constraint de stage scores para incluir score_type
ALTER TABLE tournament_admin_stage_scores
    DROP CONSTRAINT IF EXISTS tournament_admin_stage_scores_stage_id_player_id_key;
ALTER TABLE tournament_admin_stage_scores
    ADD CONSTRAINT uq_stage_score_stage_player_type UNIQUE (stage_id, player_id, score_type);

-- 9. Agregar score_type a tournament_admin_playoff_results (HCP o SCRATCH)
ALTER TABLE tournament_admin_playoff_results
    ADD COLUMN score_type VARCHAR(20) NOT NULL DEFAULT 'HCP';

-- 10. Actualizar la unique constraint de playoff results para incluir score_type
ALTER TABLE tournament_admin_playoff_results
    DROP CONSTRAINT IF EXISTS tournament_admin_playoff_results_tournament_admin_id_player_id_key;
ALTER TABLE tournament_admin_playoff_results
    ADD CONSTRAINT uq_playoff_result_admin_player_type UNIQUE (tournament_admin_id, player_id, score_type);
