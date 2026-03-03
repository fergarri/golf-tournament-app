ALTER TABLE players
    ADD COLUMN sexo VARCHAR(1);

UPDATE players
SET sexo = 'M'
WHERE sexo IS NULL;

ALTER TABLE players
    ALTER COLUMN sexo SET NOT NULL;

ALTER TABLE players
    ADD CONSTRAINT chk_players_sexo
    CHECK (sexo IN ('M', 'F'));

ALTER TABLE tournaments
    ADD COLUMN tee_masculino_id BIGINT NULL REFERENCES course_tees(id),
    ADD COLUMN tee_femenino_id BIGINT NULL REFERENCES course_tees(id);

UPDATE tournaments
SET tee_masculino_id = tee_id,
    tee_femenino_id = tee_id
WHERE tee_id IS NOT NULL;

CREATE INDEX idx_tournaments_tee_masculino_id ON tournaments(tee_masculino_id);
CREATE INDEX idx_tournaments_tee_femenino_id ON tournaments(tee_femenino_id);

DROP INDEX IF EXISTS idx_tournaments_tee_id;
ALTER TABLE tournaments DROP COLUMN IF EXISTS tee_id;
