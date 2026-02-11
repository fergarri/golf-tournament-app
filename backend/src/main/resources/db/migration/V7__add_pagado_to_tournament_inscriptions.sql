-- Agregar columna pagado a tournament_inscriptions
ALTER TABLE tournament_inscriptions 
ADD COLUMN pagado BOOLEAN NOT NULL DEFAULT FALSE;
