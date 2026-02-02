-- Users table (Admin users)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    matricula VARCHAR(50) UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ADMIN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Players table
CREATE TABLE players (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    matricula VARCHAR(50) NOT NULL UNIQUE,
    fecha_nacimiento DATE,
    handicap_index DECIMAL(4,1),
    telefono VARCHAR(50),
    club_origen VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Courses table (Golf courses)
CREATE TABLE courses (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    pais VARCHAR(100) NOT NULL,
    provincia VARCHAR(100),
    ciudad VARCHAR(100),
    cantidad_hoyos INTEGER NOT NULL DEFAULT 18,
    course_rating DECIMAL(4,1),
    slope_rating INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Course tees (Salidas del campo)
CREATE TABLE course_tees (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    nombre VARCHAR(100) NOT NULL,
    grupo VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_course_tees_course_id ON course_tees(course_id);
CREATE INDEX idx_course_tees_active ON course_tees(active);

-- Holes table
CREATE TABLE holes (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    numero_hoyo INTEGER NOT NULL,
    par INTEGER NOT NULL,
    handicap INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_course_hole UNIQUE (course_id, numero_hoyo)
);

CREATE INDEX idx_holes_course_id ON holes(course_id);

-- Hole distances (Distancias por salida)
CREATE TABLE hole_distances (
    id BIGSERIAL PRIMARY KEY,
    hole_id BIGINT NOT NULL REFERENCES holes(id) ON DELETE CASCADE,
    course_tee_id BIGINT NOT NULL REFERENCES course_tees(id) ON DELETE CASCADE,
    distancia_yardas INTEGER NOT NULL,
    CONSTRAINT unique_hole_tee_distance UNIQUE (hole_id, course_tee_id)
);

CREATE INDEX idx_hole_distances_hole_id ON hole_distances(hole_id);
CREATE INDEX idx_hole_distances_course_tee_id ON hole_distances(course_tee_id);

-- Tournaments table
CREATE TABLE tournaments (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    tipo VARCHAR(50) NOT NULL DEFAULT 'CLASICO',
    modalidad VARCHAR(50) NOT NULL DEFAULT 'MEDAL_PLAY',
    course_id BIGINT NOT NULL REFERENCES courses(id),
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE,
    limite_inscriptos INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tournaments_codigo ON tournaments(codigo);
CREATE INDEX idx_tournaments_course_id ON tournaments(course_id);

-- Tournament tee configuration
CREATE TABLE tournament_tee_configs (
    id BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    course_tee_id_primeros_9 BIGINT NOT NULL REFERENCES course_tees(id),
    course_tee_id_segundos_9 BIGINT REFERENCES course_tees(id),
    CONSTRAINT unique_tournament_tee_config UNIQUE (tournament_id)
);

CREATE INDEX idx_tournament_tee_configs_tournament_id ON tournament_tee_configs(tournament_id);

-- Tournament categories
CREATE TABLE tournament_categories (
    id BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    nombre VARCHAR(100) NOT NULL,
    handicap_min DECIMAL(4,1) NOT NULL,
    handicap_max DECIMAL(4,1) NOT NULL
);

CREATE INDEX idx_tournament_categories_tournament_id ON tournament_categories(tournament_id);

-- Tournament inscriptions
CREATE TABLE tournament_inscriptions (
    id BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    category_id BIGINT REFERENCES tournament_categories(id),
    handicap_course DECIMAL(4,1),
    fecha_inscripcion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_tournament_player_inscription UNIQUE (tournament_id, player_id)
);

CREATE INDEX idx_tournament_inscriptions_tournament_id ON tournament_inscriptions(tournament_id);
CREATE INDEX idx_tournament_inscriptions_player_id ON tournament_inscriptions(player_id);

-- Scorecards
CREATE TABLE scorecards (
    id BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    marker_id BIGINT REFERENCES players(id),
    delivered BOOLEAN NOT NULL DEFAULT FALSE,
    delivered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_tournament_player_scorecard UNIQUE (tournament_id, player_id)
);

CREATE INDEX idx_scorecards_tournament_id ON scorecards(tournament_id);
CREATE INDEX idx_scorecards_player_id ON scorecards(player_id);
CREATE INDEX idx_scorecards_delivered ON scorecards(delivered);

-- Hole scores
CREATE TABLE hole_scores (
    id BIGSERIAL PRIMARY KEY,
    scorecard_id BIGINT NOT NULL REFERENCES scorecards(id) ON DELETE CASCADE,
    hole_id BIGINT NOT NULL REFERENCES holes(id),
    golpes_propio INTEGER,
    golpes_marcador INTEGER,
    validado BOOLEAN GENERATED ALWAYS AS (
        CASE 
            WHEN golpes_propio IS NOT NULL AND golpes_marcador IS NOT NULL 
            THEN golpes_propio = golpes_marcador
            ELSE FALSE
        END
    ) STORED,
    CONSTRAINT unique_scorecard_hole UNIQUE (scorecard_id, hole_id)
);

CREATE INDEX idx_hole_scores_scorecard_id ON hole_scores(scorecard_id);
CREATE INDEX idx_hole_scores_hole_id ON hole_scores(hole_id);
CREATE INDEX idx_hole_scores_validado ON hole_scores(validado);
