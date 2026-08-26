-- Multi-tenant: se vincula cada jugador a un club (course) a partir del texto libre
-- "club_origen" ya cargado, para poder filtrar "jugadores de mi club" en la UI.
-- Los jugadores siguen siendo visibles en toda la app (no se restringe el acceso).

-- 1) Normalizar el texto de club_origen a Title Case (ej. "CARANDAY GOLF CLUB" -> "Caranday Golf Club")
--    para poder unificar variantes de mayúsculas/minúsculas antes de matchear/crear canchas.
UPDATE players
SET club_origen = initcap(trim(club_origen))
WHERE club_origen IS NOT NULL AND trim(club_origen) <> '';

ALTER TABLE players ADD COLUMN course_id BIGINT REFERENCES courses(id);
CREATE INDEX idx_players_course_id ON players(course_id);

-- 2) Crear las canchas/clubes faltantes para los valores de club_origen que no matchean
--    (case-insensitive) ninguna cancha ya existente.
INSERT INTO courses (nombre, pais, cantidad_hoyos)
SELECT DISTINCT p.club_origen, 'Argentina', 18
FROM players p
WHERE p.club_origen IS NOT NULL AND trim(p.club_origen) <> ''
  AND NOT EXISTS (
      SELECT 1 FROM courses c WHERE LOWER(c.nombre) = LOWER(p.club_origen)
  );

-- 3) Asignar course_id a cada jugador según el match (case-insensitive) de club_origen.
UPDATE players p
SET course_id = c.id
FROM courses c
WHERE p.club_origen IS NOT NULL AND trim(p.club_origen) <> ''
  AND LOWER(c.nombre) = LOWER(p.club_origen);
