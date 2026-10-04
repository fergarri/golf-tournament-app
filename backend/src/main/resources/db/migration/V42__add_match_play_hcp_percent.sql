-- Porcentaje de HCP Course para el Match Play de la llave Con HCP.
-- Los partidos ya existentes siguen cargando golpes (entry_mode STROKES).
-- Los partidos nuevos marcan el ganador del hoyo (entry_mode WINNER).

ALTER TABLE tournament_admin_scoring_config
    ADD COLUMN match_play_hcp_percent NUMERIC(5, 1) NOT NULL DEFAULT 100.0;

ALTER TABLE tournament_admin_playoff_matches
    ADD COLUMN entry_mode VARCHAR(20) NOT NULL DEFAULT 'STROKES',
    ADD COLUMN hcp_allowance_percent NUMERIC(5, 1);

ALTER TABLE tournament_admin_playoff_match_cards
    ADD COLUMN handicap_course_unrounded NUMERIC(28, 20);

ALTER TABLE tournament_admin_playoff_match_hole_scores
    ADD COLUMN hole_result VARCHAR(10);
