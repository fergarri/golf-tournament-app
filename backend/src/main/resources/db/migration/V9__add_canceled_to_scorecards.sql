-- Agregar columna canceled a la tabla scorecards
ALTER TABLE scorecards
ADD COLUMN canceled BOOLEAN NOT NULL DEFAULT false;

-- Crear índice para búsquedas por canceled
CREATE INDEX idx_scorecards_canceled ON scorecards(canceled);
