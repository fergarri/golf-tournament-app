-- Los partidos que todavía no tienen golpes cargados pasan a marcar el ganador del hoyo.
-- Los que ya tienen golpes siguen con la tarjeta anterior.

UPDATE tournament_admin_playoff_matches m
SET entry_mode = 'WINNER'
WHERE NOT EXISTS (
    SELECT 1
    FROM tournament_admin_playoff_match_cards c
    JOIN tournament_admin_playoff_match_hole_scores h ON h.card_id = c.id
    WHERE c.match_id = m.id
      AND (h.golpes_propio IS NOT NULL OR h.golpes_rival IS NOT NULL)
);
