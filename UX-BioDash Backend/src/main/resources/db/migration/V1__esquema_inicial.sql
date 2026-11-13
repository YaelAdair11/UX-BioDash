-- =====================================================================
-- UX-BioDash - Modelo de persistencia (PostgreSQL 17)
-- Basado en "Definicion de la Estructura de Datos Comun y Modelo de Persistencia"
-- =====================================================================

-- 1. proyecto
CREATE TABLE proyecto (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(120) NOT NULL,
    descripcion TEXT,
    creado_en   TIMESTAMP    NOT NULL DEFAULT now()
);

-- 2. estimulo
CREATE TABLE estimulo (
    id                BIGSERIAL PRIMARY KEY,
    proyecto_id       BIGINT       NOT NULL REFERENCES proyecto (id) ON DELETE CASCADE,
    nombre_archivo    VARCHAR(255) NOT NULL,
    ancho_original_px INTEGER      NOT NULL CHECK (ancho_original_px > 0),
    alto_original_px  INTEGER      NOT NULL CHECK (alto_original_px > 0),
    url_recurso       VARCHAR(500)
);
CREATE INDEX idx_estimulo_proyecto ON estimulo (proyecto_id);

-- 2.b estimulo_imagen: contenido binario de la captura (1:1 con estimulo).
-- Se separa de "estimulo" para no cargar los bytes en cada consulta.
CREATE TABLE estimulo_imagen (
    estimulo_id   BIGINT PRIMARY KEY REFERENCES estimulo (id) ON DELETE CASCADE,
    tipo_mime     VARCHAR(50) NOT NULL,
    tamano_bytes  BIGINT      NOT NULL,
    contenido     BYTEA       NOT NULL
);

-- 3. sesion_evaluacion
CREATE TABLE sesion_evaluacion (
    id                 BIGSERIAL PRIMARY KEY,
    proyecto_id        BIGINT      NOT NULL REFERENCES proyecto (id) ON DELETE CASCADE,
    estimulo_id        BIGINT      NOT NULL REFERENCES estimulo (id) ON DELETE CASCADE,
    codigo_sujeto      VARCHAR(50) NOT NULL,
    dispositivo_origen VARCHAR(50) NOT NULL,
    duracion_total_ms  INTEGER,
    tasa_muestreo_hz   NUMERIC(6, 2)
);
CREATE INDEX idx_sesion_proyecto ON sesion_evaluacion (proyecto_id);
CREATE INDEX idx_sesion_estimulo ON sesion_evaluacion (estimulo_id);
CREATE INDEX idx_sesion_sujeto ON sesion_evaluacion (estimulo_id, codigo_sujeto);

-- 4. area_interes (AOI)
CREATE TABLE area_interes (
    id               BIGSERIAL PRIMARY KEY,
    estimulo_id      BIGINT       NOT NULL REFERENCES estimulo (id) ON DELETE CASCADE,
    etiqueta         VARCHAR(100) NOT NULL,
    forma_geometrica VARCHAR(30)  NOT NULL CHECK (forma_geometrica IN ('RECTANGULO', 'POLIGONO')),
    coordenadas_json JSONB        NOT NULL
);
CREATE INDEX idx_aoi_estimulo ON area_interes (estimulo_id);

-- 5. punto_gaze_muestra: muestras crudas ya normalizadas
CREATE TABLE punto_gaze_muestra (
    id              BIGSERIAL PRIMARY KEY,
    sesion_id       BIGINT           NOT NULL REFERENCES sesion_evaluacion (id) ON DELETE CASCADE,
    tiempo_ms       INTEGER          NOT NULL,
    x_normalizado   DOUBLE PRECISION NOT NULL CHECK (x_normalizado BETWEEN 0.0 AND 1.0),
    y_normalizado   DOUBLE PRECISION NOT NULL CHECK (y_normalizado BETWEEN 0.0 AND 1.0),
    diametro_pupila DOUBLE PRECISION
);
CREATE INDEX idx_muestra_sesion_tiempo ON punto_gaze_muestra (sesion_id, tiempo_ms);

-- 6. fijacion
CREATE TABLE fijacion (
    id              BIGSERIAL PRIMARY KEY,
    sesion_id       BIGINT           NOT NULL REFERENCES sesion_evaluacion (id) ON DELETE CASCADE,
    orden_secuencia INTEGER          NOT NULL,
    inicio_ms       INTEGER          NOT NULL,
    fin_ms          INTEGER          NOT NULL,
    duracion_ms     INTEGER          NOT NULL,
    x_normalizado   DOUBLE PRECISION NOT NULL CHECK (x_normalizado BETWEEN 0.0 AND 1.0),
    y_normalizado   DOUBLE PRECISION NOT NULL CHECK (y_normalizado BETWEEN 0.0 AND 1.0),
    aoi_id          BIGINT REFERENCES area_interes (id) ON DELETE SET NULL,
    CONSTRAINT uq_fijacion_orden UNIQUE (sesion_id, orden_secuencia)
);
CREATE INDEX idx_fijacion_aoi ON fijacion (aoi_id);

-- 7. sacada
CREATE TABLE sacada (
    id                      BIGSERIAL PRIMARY KEY,
    sesion_id               BIGINT NOT NULL REFERENCES sesion_evaluacion (id) ON DELETE CASCADE,
    fijacion_origen_id      BIGINT NOT NULL REFERENCES fijacion (id) ON DELETE CASCADE,
    fijacion_destino_id     BIGINT NOT NULL REFERENCES fijacion (id) ON DELETE CASCADE,
    amplitud_grados         DOUBLE PRECISION,
    velocidad_pico_grados_s DOUBLE PRECISION
);
CREATE INDEX idx_sacada_sesion ON sacada (sesion_id);

-- 8. metrica_aoi_resultado: metricas precalculadas por AOI y sesion
CREATE TABLE metrica_aoi_resultado (
    id                         BIGSERIAL PRIMARY KEY,
    aoi_id                     BIGINT    NOT NULL REFERENCES area_interes (id) ON DELETE CASCADE,
    sesion_id                  BIGINT    NOT NULL REFERENCES sesion_evaluacion (id) ON DELETE CASCADE,
    conteo_fijaciones          INTEGER   NOT NULL,
    duracion_media_fijacion_ms DOUBLE PRECISION,
    dwell_time_ms              INTEGER   NOT NULL,
    porcentaje_dwell           DOUBLE PRECISION,
    ttff_ms                    INTEGER,
    numero_visitas             INTEGER   NOT NULL,
    calculado_en               TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_metrica_aoi_sesion UNIQUE (aoi_id, sesion_id)
);
CREATE INDEX idx_metrica_sesion ON metrica_aoi_resultado (sesion_id);
