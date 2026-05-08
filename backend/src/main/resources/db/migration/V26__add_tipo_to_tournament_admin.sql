-- Agregar tipo al torneo administrativo
ALTER TABLE tournament_admins ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'FRUTALES';

-- Inferir el tipo de los admins existentes según los torneos que ya tienen relacionados
UPDATE tournament_admins ta
SET tipo = 'FRUTALES'
WHERE EXISTS (
    SELECT 1 FROM tournament_admin_related_tournaments tart
    JOIN tournaments t ON t.id = tart.tournament_id
    WHERE tart.tournament_admin_id = ta.id AND t.tipo = 'FRUTALES'
);

UPDATE tournament_admins ta
SET tipo = 'CLASICO'
WHERE NOT EXISTS (
    SELECT 1 FROM tournament_admin_related_tournaments tart
    JOIN tournaments t ON t.id = tart.tournament_id
    WHERE tart.tournament_admin_id = ta.id AND t.tipo = 'FRUTALES'
) AND EXISTS (
    SELECT 1 FROM tournament_admin_related_tournaments tart
    WHERE tart.tournament_admin_id = ta.id
);

-- Eliminar la tabla de relación directa (ya no se usa)
DROP TABLE IF EXISTS tournament_admin_related_tournaments;
