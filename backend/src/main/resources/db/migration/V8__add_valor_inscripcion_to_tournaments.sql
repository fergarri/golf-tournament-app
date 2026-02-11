-- Agregar columna valor_inscripcion a tournaments
ALTER TABLE tournaments 
ADD COLUMN valor_inscripcion DECIMAL(10, 2) DEFAULT 0.00;
