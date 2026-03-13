ALTER TABLE tournament_categories
    ADD COLUMN sexo_categoria CHAR(1);

UPDATE tournament_categories
SET sexo_categoria = 'X'
WHERE sexo_categoria IS NULL;

ALTER TABLE tournament_categories
    ALTER COLUMN sexo_categoria SET DEFAULT 'X',
    ALTER COLUMN sexo_categoria SET NOT NULL;

ALTER TABLE tournament_categories
    ADD CONSTRAINT chk_tournament_categories_sexo
        CHECK (sexo_categoria IN ('M', 'F', 'X'));
