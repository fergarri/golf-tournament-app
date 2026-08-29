ALTER TABLE course_tees
    ADD COLUMN genero VARCHAR(1) NOT NULL DEFAULT 'M';

ALTER TABLE course_tees
    ADD CONSTRAINT chk_course_tees_genero CHECK (genero IN ('M', 'F'));
