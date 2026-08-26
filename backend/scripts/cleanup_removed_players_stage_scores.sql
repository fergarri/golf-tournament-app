-- =====================================================================
-- Script MANUAL (no es una migración Flyway, no se ejecuta automáticamente)
--
-- Objetivo: eliminar de las tablas de puntuación (etapas y playoff) a los
-- jugadores que ya fueron dados de baja (eliminada su inscripción) de un
-- Torneo Administrativo, pero que quedaron con registros de puntos
-- persistidos de un cálculo anterior a la baja.
--
-- Un jugador se considera "dado de baja" si NO existe una fila en
-- tournament_admin_inscriptions para (tournament_admin_id, player_id).
--
-- Cómo usarlo:
--   1) Ejecutar primero los dos SELECT de verificación (pasos 1 y 2) y
--      revisar que las filas listadas sean efectivamente las que se
--      quieren eliminar.
--   2) Ejecutar los DELETE (pasos 3 y 4) dentro de la misma transacción.
--   3) Si todo está OK, hacer COMMIT. Si algo no coincide, hacer ROLLBACK.
--
-- Después de correr el script, conviene volver a presionar "Calcular
-- Puntos" en la vista de Playoff del Torneo Administrativo afectado,
-- para que las posiciones (1º, 2º, 3º, ...) se re-numeren sin los huecos
-- que deja la baja de esos jugadores.
-- =====================================================================

BEGIN;

-- -----------------------------------------------------------------------
-- 1) Verificación previa: filas de ETAPAS (tournament_admin_stage_scores)
--    que se van a borrar porque el jugador ya no está inscripto en el
--    Torneo Administrativo dueño de esa etapa.
-- -----------------------------------------------------------------------
SELECT
    tas.id                     AS stage_score_id,
    stg.tournament_admin_id,
    stg.nombre                 AS stage_nombre,
    tas.player_id,
    p.apellido,
    p.nombre,
    tas.score_type,
    tas.total_points,
    tas.position
FROM tournament_admin_stage_scores tas
JOIN tournament_admin_stages stg ON stg.id = tas.stage_id
JOIN players p ON p.id = tas.player_id
WHERE NOT EXISTS (
    SELECT 1
    FROM tournament_admin_inscriptions tai
    WHERE tai.tournament_admin_id = stg.tournament_admin_id
      AND tai.player_id = tas.player_id
)
ORDER BY stg.tournament_admin_id, tas.stage_id, tas.score_type, p.apellido;

-- -----------------------------------------------------------------------
-- 2) Verificación previa: filas de PLAYOFF (tournament_admin_playoff_results)
--    que se van a borrar por el mismo motivo.
-- -----------------------------------------------------------------------
SELECT
    tpr.id                     AS playoff_result_id,
    tpr.tournament_admin_id,
    tpr.player_id,
    p.apellido,
    p.nombre,
    tpr.score_type,
    tpr.total_points,
    tpr.position,
    tpr.qualified
FROM tournament_admin_playoff_results tpr
JOIN players p ON p.id = tpr.player_id
WHERE NOT EXISTS (
    SELECT 1
    FROM tournament_admin_inscriptions tai
    WHERE tai.tournament_admin_id = tpr.tournament_admin_id
      AND tai.player_id = tpr.player_id
)
ORDER BY tpr.tournament_admin_id, tpr.score_type, p.apellido;

-- -----------------------------------------------------------------------
-- 3) Borrar filas de ETAPAS (tournament_admin_stage_scores) de jugadores
--    dados de baja.
-- -----------------------------------------------------------------------
DELETE FROM tournament_admin_stage_scores tas
WHERE NOT EXISTS (
    SELECT 1
    FROM tournament_admin_stages stg
    JOIN tournament_admin_inscriptions tai ON tai.tournament_admin_id = stg.tournament_admin_id
    WHERE stg.id = tas.stage_id
      AND tai.player_id = tas.player_id
);

-- -----------------------------------------------------------------------
-- 4) Borrar filas de PLAYOFF (tournament_admin_playoff_results) de
--    jugadores dados de baja.
-- -----------------------------------------------------------------------
DELETE FROM tournament_admin_playoff_results tpr
WHERE NOT EXISTS (
    SELECT 1
    FROM tournament_admin_inscriptions tai
    WHERE tai.tournament_admin_id = tpr.tournament_admin_id
      AND tai.player_id = tpr.player_id
);

-- Revisar que los resultados sean los esperados antes de confirmar.
COMMIT;
-- Si algo salió mal, usar ROLLBACK; en lugar de COMMIT;
