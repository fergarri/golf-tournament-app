-- Control cruzado de hoyos entre marcadores
ALTER TABLE tournaments ADD COLUMN control_cruzado BOOLEAN NOT NULL DEFAULT FALSE;

-- Reemplazar la columna GENERATED por una columna normal (el valor lo calcula el servicio)
ALTER TABLE hole_scores DROP COLUMN validado;
ALTER TABLE hole_scores ADD COLUMN validado BOOLEAN NOT NULL DEFAULT FALSE;

-- Flag agregado en scorecard: true cuando todos los hoyos marcados están validados
ALTER TABLE scorecards ADD COLUMN marcador_validado BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_hole_scores_validado ON hole_scores(validado);
CREATE INDEX idx_scorecards_marcador_validado ON scorecards(marcador_validado);
