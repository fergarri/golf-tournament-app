ALTER TABLE tournaments
    ADD COLUMN cantidad_hoyos_juego INTEGER;

UPDATE tournaments t
SET cantidad_hoyos_juego = c.cantidad_hoyos
FROM courses c
WHERE c.id = t.course_id
  AND t.cantidad_hoyos_juego IS NULL;

ALTER TABLE tournaments
    ADD CONSTRAINT chk_tournaments_cantidad_hoyos_juego
    CHECK (cantidad_hoyos_juego IN (9, 18));

ALTER TABLE scorecards
    ADD COLUMN tee_id BIGINT NULL REFERENCES course_tees(id),
    ADD COLUMN cantidad_hoyos_juego INTEGER;

UPDATE scorecards sc
SET tee_id = CASE
                 WHEN p.sexo = 'F' THEN t.tee_femenino_id
                 ELSE t.tee_masculino_id
             END,
    cantidad_hoyos_juego = t.cantidad_hoyos_juego
FROM tournaments t,
     players p
WHERE sc.tournament_id = t.id
  AND sc.player_id = p.id;

ALTER TABLE scorecards
    ADD CONSTRAINT chk_scorecards_cantidad_hoyos_juego
    CHECK (cantidad_hoyos_juego IN (9, 18));

UPDATE scorecards
SET status = 'PENDING_CONFIG',
    handicap_course = NULL
WHERE status = 'IN_PROGRESS'
  AND (tee_id IS NULL OR cantidad_hoyos_juego IS NULL);

CREATE INDEX idx_scorecards_tee_id ON scorecards(tee_id);
