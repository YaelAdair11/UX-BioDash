-- Alineacion con docs/API_CONTRACT.md del frontend.

ALTER TABLE proyecto
    ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR'
        CHECK (estado IN ('BORRADOR', 'EN_INGESTA', 'EN_ANALISIS', 'COMPLETADO')),
    ADD COLUMN dispositivo VARCHAR(100),
    ADD COLUMN modificado_en TIMESTAMP;

ALTER TABLE estimulo
    ADD COLUMN etiqueta_dispositivo VARCHAR(50);

ALTER TABLE area_interes
    ADD COLUMN color VARCHAR(20);
