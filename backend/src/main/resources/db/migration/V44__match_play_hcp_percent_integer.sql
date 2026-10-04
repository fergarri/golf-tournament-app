-- El porcentaje de HCP Course del Match Play se guarda como entero (0 a 100).

ALTER TABLE tournament_admin_scoring_config
    ALTER COLUMN match_play_hcp_percent TYPE NUMERIC(5, 0)
    USING ROUND(match_play_hcp_percent);

ALTER TABLE tournament_admin_scoring_config
    ALTER COLUMN match_play_hcp_percent SET DEFAULT 100;
