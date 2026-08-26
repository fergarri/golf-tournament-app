-- Nuevo rol ADMIN_CLUB: nivel intermedio entre ADMIN (superadmin) y USER (admin básico de club).
-- Puede crear/gestionar usuarios (USER y ADMIN_CLUB) de su propio club y, a diferencia de USER,
-- puede eliminar players, torneos, torneos administrativos, tees y hoyos.

ALTER TABLE users DROP CONSTRAINT chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'ADMIN_CLUB', 'USER'));
