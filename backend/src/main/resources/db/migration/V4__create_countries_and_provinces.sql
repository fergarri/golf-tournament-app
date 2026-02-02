-- Countries table
CREATE TABLE countries (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    codigo_iso VARCHAR(2) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Provinces table
CREATE TABLE provinces (
    id BIGSERIAL PRIMARY KEY,
    country_id BIGINT NOT NULL REFERENCES countries(id) ON DELETE CASCADE,
    nombre VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_country_province UNIQUE (country_id, nombre)
);

CREATE INDEX idx_provinces_country_id ON provinces(country_id);

-- Insert some initial countries (you can add more as needed)
INSERT INTO countries (nombre, codigo_iso) VALUES 
('Argentina', 'AR'),
('Uruguay', 'UY'),
('Chile', 'CL'),
('Brasil', 'BR'),
('Paraguay', 'PY'),
('Estados Unidos', 'US'),
('España', 'ES'),
('México', 'MX');

-- Insert provinces for Argentina
INSERT INTO provinces (country_id, nombre) 
SELECT id, unnest(ARRAY[
    'Buenos Aires',
    'Catamarca',
    'Chaco',
    'Chubut',
    'Ciudad Autónoma de Buenos Aires',
    'Córdoba',
    'Corrientes',
    'Entre Ríos',
    'Formosa',
    'Jujuy',
    'La Pampa',
    'La Rioja',
    'Mendoza',
    'Misiones',
    'Neuquén',
    'Río Negro',
    'Salta',
    'San Juan',
    'San Luis',
    'Santa Cruz',
    'Santa Fe',
    'Santiago del Estero',
    'Tierra del Fuego',
    'Tucumán'
])
FROM countries WHERE codigo_iso = 'AR';

-- Insert provinces for Uruguay
INSERT INTO provinces (country_id, nombre) 
SELECT id, unnest(ARRAY[
    'Artigas',
    'Canelones',
    'Cerro Largo',
    'Colonia',
    'Durazno',
    'Flores',
    'Florida',
    'Lavalleja',
    'Maldonado',
    'Montevideo',
    'Paysandú',
    'Río Negro',
    'Rivera',
    'Rocha',
    'Salto',
    'San José',
    'Soriano',
    'Tacuarembó',
    'Treinta y Tres'
])
FROM countries WHERE codigo_iso = 'UY';

-- Insert provinces for Chile
INSERT INTO provinces (country_id, nombre) 
SELECT id, unnest(ARRAY[
    'Región de Arica y Parinacota',
    'Región de Tarapacá',
    'Región de Antofagasta',
    'Región de Atacama',
    'Región de Coquimbo',
    'Región de Valparaíso',
    'Región Metropolitana de Santiago',
    'Región del Libertador General Bernardo O''Higgins',
    'Región del Maule',
    'Región de Ñuble',
    'Región del Biobío',
    'Región de La Araucanía',
    'Región de Los Ríos',
    'Región de Los Lagos',
    'Región de Aysén',
    'Región de Magallanes'
])
FROM countries WHERE codigo_iso = 'CL';
