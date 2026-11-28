CREATE TABLE HistorialSeccionMatricula (
    id_historial_seccion_matricula BIGINT NOT NULL AUTO_INCREMENT,
    id_matricula BIGINT NOT NULL,
    id_seccion_anterior BIGINT NOT NULL,
    fecha_cambio DATETIME(6) NOT NULL,
    fecha_cambio_local DATETIME(6) NOT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    activo BIT(1) NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id_historial_seccion_matricula),
    CONSTRAINT fk_historial_seccion_matricula FOREIGN KEY (id_matricula) REFERENCES Matricula (id_matricula),
    CONSTRAINT fk_historial_seccion_anterior FOREIGN KEY (id_seccion_anterior) REFERENCES Seccion (id_seccion)
);

CREATE INDEX ix_historial_seccion_matricula ON HistorialSeccionMatricula (id_matricula, id_seccion_anterior);
