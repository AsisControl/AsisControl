-- US-13 / US-48. Reutiliza obligaciones, personas, transacciones y comprobantes existentes.
-- El ENUM anterior no admite los medios simulados; VARCHAR preserva todos sus valores legados.
ALTER TABLE TransaccionPago MODIFY COLUMN medio_pago VARCHAR(40) NOT NULL;

CREATE TABLE OrdenPago (
    id_orden_pago BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    activo BIT NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    codigo VARCHAR(45) NOT NULL,
    id_apoderado BIGINT NOT NULL,
    clave_idempotencia VARCHAR(36) NOT NULL,
    seleccion_hash VARCHAR(64) NOT NULL,
    metodo VARCHAR(12) NOT NULL,
    estado VARCHAR(30) NOT NULL,
    monto_base DECIMAL(12,2) NOT NULL,
    mora DECIMAL(12,2) NOT NULL,
    monto_total DECIMAL(12,2) NOT NULL,
    moneda VARCHAR(3) NOT NULL,
    creada_en DATETIME(6) NOT NULL,
    expira_en DATETIME(6) NOT NULL,
    pagada_en DATETIME(6),
    payment_intent_id VARCHAR(80),
    mensaje VARCHAR(300),
    CONSTRAINT uk_orden_pago_codigo UNIQUE (codigo),
    CONSTRAINT uk_orden_pago_idempotencia UNIQUE (id_apoderado, clave_idempotencia),
    CONSTRAINT uk_orden_pago_intento UNIQUE (payment_intent_id),
    CONSTRAINT fk_orden_pago_apoderado FOREIGN KEY (id_apoderado) REFERENCES Apoderado (id_apoderado)
) ENGINE=InnoDB;

CREATE TABLE OrdenPagoDetalle (
    id_orden_pago_detalle BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    activo BIT NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    id_orden_pago BIGINT NOT NULL,
    id_compromiso_pago BIGINT NOT NULL,
    id_transaccion_pago BIGINT NOT NULL,
    monto DECIMAL(12,2) NOT NULL,
    codigo_compromiso VARCHAR(50) NOT NULL,
    alumno VARCHAR(300) NOT NULL,
    concepto VARCHAR(160) NOT NULL,
    numero_cuota INTEGER,
    CONSTRAINT uk_orden_detalle_compromiso UNIQUE (id_orden_pago, id_compromiso_pago),
    CONSTRAINT uk_orden_detalle_transaccion UNIQUE (id_transaccion_pago),
    CONSTRAINT fk_orden_detalle_orden FOREIGN KEY (id_orden_pago) REFERENCES OrdenPago (id_orden_pago),
    CONSTRAINT fk_orden_detalle_compromiso FOREIGN KEY (id_compromiso_pago) REFERENCES CompromisoPago (id_compromiso_pago),
    CONSTRAINT fk_orden_detalle_transaccion FOREIGN KEY (id_transaccion_pago) REFERENCES TransaccionPago (id_transaccion_pago)
) ENGINE=InnoDB;

CREATE INDEX ix_orden_pago_apoderado_fecha ON OrdenPago (id_apoderado, creada_en);
CREATE INDEX ix_orden_pago_estado_expira ON OrdenPago (estado, expira_en);
