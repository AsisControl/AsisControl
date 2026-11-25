CREATE TABLE ReglaCalificacion (
    id_regla_calificacion BIGINT NOT NULL AUTO_INCREMENT,
    id_curso BIGINT NOT NULL,
    id_grado_academico BIGINT NOT NULL,
    numero_version INT NOT NULL,
    estado VARCHAR(20) NOT NULL,
    formula VARCHAR(30) NOT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    activo BIT(1) NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id_regla_calificacion),
    CONSTRAINT uk_regla_curso_grado_numero UNIQUE (id_curso, id_grado_academico, numero_version),
    CONSTRAINT fk_regla_curso FOREIGN KEY (id_curso) REFERENCES Curso (id_curso),
    CONSTRAINT fk_regla_grado FOREIGN KEY (id_grado_academico) REFERENCES GradoAcademico (id_grado_academico)
);

CREATE TABLE ReglaCalificacionComponente (
    id_regla_calificacion BIGINT NOT NULL,
    tipo_evaluacion VARCHAR(32) NOT NULL,
    nombre VARCHAR(90) NOT NULL,
    peso DECIMAL(5, 2) NOT NULL,
    PRIMARY KEY (id_regla_calificacion, tipo_evaluacion),
    CONSTRAINT fk_componente_regla FOREIGN KEY (id_regla_calificacion) REFERENCES ReglaCalificacion (id_regla_calificacion)
);

ALTER TABLE InformeProgreso ADD COLUMN id_regla_calificacion BIGINT NULL;
ALTER TABLE InformeProgreso ADD COLUMN consolidado_regla LONGTEXT NULL;
ALTER TABLE InformeProgreso ADD CONSTRAINT fk_informe_regla_calificacion
    FOREIGN KEY (id_regla_calificacion) REFERENCES ReglaCalificacion (id_regla_calificacion);
