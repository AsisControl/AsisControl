-- Conserva la regla utilizada en cada nueva marcación sin reinterpretar el historial anterior.
ALTER TABLE AsistenciaDocente
    ADD COLUMN ventana_apertura DATETIME(6) NULL,
    ADD COLUMN limite_puntualidad DATETIME(6) NULL,
    ADD COLUMN ventana_cierre DATETIME(6) NULL;

CREATE TABLE JornadaMarcacionDocente (
    id_jornada_marcacion BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    id_docente BIGINT NOT NULL,
    fecha DATE NOT NULL,
    apertura DATETIME(6) NOT NULL,
    limite_puntualidad DATETIME(6) NOT NULL,
    cierre DATETIME(6) NOT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    activo BIT(1) NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_jornada_docente_fecha UNIQUE (id_docente, fecha),
    CONSTRAINT fk_jornada_marcacion_docente FOREIGN KEY (id_docente) REFERENCES Docente(id_docente)
);
