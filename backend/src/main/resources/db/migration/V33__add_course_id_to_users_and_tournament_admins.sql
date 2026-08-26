-- Multi-tenant: cada usuario y cada torneo administrativo pasa a pertenecer a un club (course).
ALTER TABLE users ADD COLUMN course_id BIGINT REFERENCES courses(id);
ALTER TABLE tournament_admins ADD COLUMN course_id BIGINT REFERENCES courses(id);

CREATE INDEX idx_users_course_id ON users(course_id);
CREATE INDEX idx_tournament_admins_course_id ON tournament_admins(course_id);

-- Backfill: se asigna el "club piloto" (la cancha existente activa, ignorando canchas
-- marcadas explícitamente como "NO USAR") a los datos ya cargados en el sistema.
DO $$
DECLARE
    pilot_course_id BIGINT;
BEGIN
    SELECT id INTO pilot_course_id
    FROM courses
    WHERE nombre NOT ILIKE '%NO USAR%'
    ORDER BY id
    LIMIT 1;

    IF pilot_course_id IS NULL THEN
        SELECT id INTO pilot_course_id FROM courses ORDER BY id LIMIT 1;
    END IF;

    IF pilot_course_id IS NOT NULL THEN
        -- Todos los usuarios existentes pasan a administrar el club piloto,
        -- salvo el usuario administrador semilla que queda como superadmin (sin club).
        UPDATE users
        SET course_id = pilot_course_id
        WHERE email <> 'admin@golftournament.com';

        UPDATE tournament_admins
        SET course_id = pilot_course_id
        WHERE course_id IS NULL;
    END IF;
END $$;

-- El usuario administrador semilla queda como superadmin: ve y administra todos los clubes.
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@golftournament.com';

-- El resto de los usuarios existentes pasa a ser "admin de club" (rol USER), acotado a su club.
UPDATE users SET role = 'USER' WHERE email <> 'admin@golftournament.com';

-- A partir de ahora todo torneo administrativo debe pertenecer a un club.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM tournament_admins WHERE course_id IS NULL) THEN
        ALTER TABLE tournament_admins ALTER COLUMN course_id SET NOT NULL;
    END IF;
END $$;
