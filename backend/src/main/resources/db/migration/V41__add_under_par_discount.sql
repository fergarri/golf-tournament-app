-- Descuenta, en la fecha siguiente, los golpes bajo par de la fecha inmediata anterior.
-- FIRST_PLACE: 1° por neto de cada categoría (en FRUTALES, el 1° del ranking único).
-- ALL_UNDER_PAR: todos los que entregaron la tarjeta con neto bajo par.
ALTER TABLE tournament_admin_scoring_config
    ADD COLUMN discount_under_par BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN under_par_discount_mode VARCHAR(30);
