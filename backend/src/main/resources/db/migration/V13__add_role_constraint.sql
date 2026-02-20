-- Create roles table
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- Create permissions table
CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- Create role_permissions junction table
CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles(id),
    permission_id BIGINT NOT NULL REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

-- Seed roles
INSERT INTO roles (name) VALUES ('ADMIN'), ('USER');

-- Seed permissions
INSERT INTO permissions (name) VALUES ('TOTAL'), ('GAMES'), ('ADMINISTRATION');

-- Assign permissions to roles: ADMIN -> TOTAL, USER -> GAMES
INSERT INTO role_permissions (role_id, permission_id)
VALUES
    ((SELECT id FROM roles WHERE name = 'ADMIN'), (SELECT id FROM permissions WHERE name = 'TOTAL')),
    ((SELECT id FROM roles WHERE name = 'USER'), (SELECT id FROM permissions WHERE name = 'GAMES'));

-- Ensure all existing users have a valid role (default to ADMIN for production users)
UPDATE users SET role = 'ADMIN' WHERE role NOT IN ('ADMIN', 'USER');

-- Add CHECK constraint to enforce only valid role values
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'USER'));
