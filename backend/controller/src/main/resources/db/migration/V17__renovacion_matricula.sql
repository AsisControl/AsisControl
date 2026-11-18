ALTER TABLE Matricula ADD COLUMN resultado_promocion VARCHAR(20);
ALTER TABLE Matricula ADD COLUMN motivo_promocion VARCHAR(700);

CREATE TABLE RenovacionMatricula (
    id_renovacion_matricula BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    id_matricula_anterior BIGINT NOT NULL,
    id_alumno BIGINT NOT NULL,
    id_seccion_destino BIGINT NOT NULL,
    anio_destino INT NOT NULL,
    grado_destino INT NOT NULL,
    estado VARCHAR(20) NOT NULL,
    clave_vigente INT,
    codigo_motivo VARCHAR(40),
    motivo VARCHAR(700),
    fecha_decision DATETIME(6),
    id_matricula_resultante BIGINT,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_renovacion_vigente UNIQUE (id_alumno, anio_destino, clave_vigente),
    CONSTRAINT uk_renovacion_matricula UNIQUE (id_matricula_resultante),
    CONSTRAINT fk_renovacion_anterior FOREIGN KEY (id_matricula_anterior) REFERENCES Matricula(id_matricula),
    CONSTRAINT fk_renovacion_alumno FOREIGN KEY (id_alumno) REFERENCES Alumno(id_alumno),
    CONSTRAINT fk_renovacion_seccion FOREIGN KEY (id_seccion_destino) REFERENCES Seccion(id_seccion),
    CONSTRAINT fk_renovacion_resultante FOREIGN KEY (id_matricula_resultante) REFERENCES Matricula(id_matricula)
) ENGINE=InnoDB;
