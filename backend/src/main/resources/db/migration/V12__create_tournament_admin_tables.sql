-- Tournament Admin tables for administrative tournament management

CREATE TABLE tournament_admins (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    fecha DATE NOT NULL,
    tournament_id BIGINT REFERENCES tournaments(id) ON DELETE SET NULL,
    valor_inscripcion DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    cantidad_cuotas INTEGER NOT NULL DEFAULT 1,
    estado VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tournament_admins_tournament_id ON tournament_admins(tournament_id);
CREATE INDEX idx_tournament_admins_estado ON tournament_admins(estado);

CREATE TABLE tournament_admin_inscriptions (
    id BIGSERIAL PRIMARY KEY,
    tournament_admin_id BIGINT NOT NULL REFERENCES tournament_admins(id) ON DELETE CASCADE,
    player_id BIGINT NOT NULL REFERENCES players(id),
    fecha_inscripcion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_tadmin_player UNIQUE (tournament_admin_id, player_id)
);

CREATE INDEX idx_tadmin_inscriptions_tadmin_id ON tournament_admin_inscriptions(tournament_admin_id);
CREATE INDEX idx_tadmin_inscriptions_player_id ON tournament_admin_inscriptions(player_id);

CREATE TABLE tournament_admin_payments (
    id BIGSERIAL PRIMARY KEY,
    inscription_id BIGINT NOT NULL REFERENCES tournament_admin_inscriptions(id) ON DELETE CASCADE,
    cuota_number INTEGER NOT NULL,
    pagado BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_inscription_cuota UNIQUE (inscription_id, cuota_number)
);

CREATE INDEX idx_tadmin_payments_inscription_id ON tournament_admin_payments(inscription_id);
