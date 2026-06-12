ALTER TABLE tournament_admin_scoring_config
    ADD COLUMN hcp_qualified_mode VARCHAR(20) NOT NULL DEFAULT 'GLOBAL';

ALTER TABLE tournament_admin_playoff_results
    ADD COLUMN category_id BIGINT;
