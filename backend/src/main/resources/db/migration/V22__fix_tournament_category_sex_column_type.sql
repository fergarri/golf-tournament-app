ALTER TABLE tournament_categories
    ALTER COLUMN sexo_categoria TYPE VARCHAR(1)
    USING TRIM(sexo_categoria);
