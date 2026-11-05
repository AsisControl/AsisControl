-- US-12: la obligación administrativa conserva su historia y su situación financiera.
-- No se asigna una cuota inventada a compromisos anteriores a US-12.
ALTER TABLE CompromisoPago ADD COLUMN numero_cuota INT NULL;
ALTER TABLE CompromisoPago ADD COLUMN concepto_normalizado VARCHAR(160) DEFAULT '' NOT NULL;
ALTER TABLE CompromisoPago ADD COLUMN estado_administrativo VARCHAR(20) DEFAULT 'ACTIVO' NOT NULL;
ALTER TABLE CompromisoPago ADD COLUMN clave_obligacion VARCHAR(64) NULL;
ALTER TABLE CompromisoPago ADD COLUMN motivo_anulacion VARCHAR(500) NULL;
ALTER TABLE CompromisoPago ADD COLUMN fecha_anulacion DATETIME(6) NULL;

UPDATE CompromisoPago
SET concepto_normalizado = LOWER(TRIM(REGEXP_REPLACE(concepto, CONCAT('[ ', CHAR(9), CHAR(10), CHAR(11), CHAR(12), CHAR(13), ']+'), ' '))),
    estado_administrativo = CASE WHEN estado_compromiso = 'ANULADO' OR activo = FALSE
                                 THEN 'ANULADO' ELSE 'ACTIVO' END,
    motivo_anulacion = CASE WHEN estado_compromiso = 'ANULADO' OR activo = FALSE
                           THEN 'Anulación anterior a US-12; consultar la auditoría histórica.' ELSE NULL END,
    fecha_anulacion = CASE WHEN estado_compromiso = 'ANULADO' OR activo = FALSE
                          THEN fecha_actualizacion ELSE NULL END;

-- La anulación anterior ocultaba el registro mediante activo=false. Recuperar su consulta.
UPDATE CompromisoPago
SET activo = TRUE, estado_compromiso = 'ANULADO'
WHERE estado_administrativo = 'ANULADO';

-- Cada nueva obligación activa usa SHA-256(alumno|año|concepto normalizado|cuota).
-- NULL libera la clave al anular; varias anulaciones pueden conservarse en el historial.
CREATE UNIQUE INDEX uk_compromiso_obligacion ON CompromisoPago (clave_obligacion);
CREATE INDEX idx_compromiso_administrativo ON CompromisoPago (estado_administrativo);

ALTER TABLE CompromisoPago
    ADD CONSTRAINT ck_compromiso_numero_cuota CHECK (numero_cuota IS NULL OR numero_cuota > 0);
ALTER TABLE CompromisoPago
    ADD CONSTRAINT ck_compromiso_estado_administrativo CHECK (estado_administrativo IN ('ACTIVO', 'ANULADO'));
