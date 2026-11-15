CREATE TABLE EvidenciaPedagogica (
    id_evidencia_pedagogica BIGINT NOT NULL AUTO_INCREMENT,
    id_asignacion_curso BIGINT NOT NULL,
    id_alumno BIGINT NOT NULL,
    id_competencia BIGINT NOT NULL,
    id_periodo_academico BIGINT NOT NULL,
    id_persona_autor BIGINT NOT NULL,
    id_archivo BIGINT NULL,
    descripcion VARCHAR(1000) NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    activo BIT(1) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id_evidencia_pedagogica),
    INDEX ix_evidencia_contexto (id_asignacion_curso, id_alumno, activo, id_periodo_academico, id_competencia),
    CONSTRAINT fk_evidencia_asignacion FOREIGN KEY (id_asignacion_curso) REFERENCES AsignacionCurso (id_asignacion_curso),
    CONSTRAINT fk_evidencia_alumno FOREIGN KEY (id_alumno) REFERENCES Alumno (id_alumno),
    CONSTRAINT fk_evidencia_competencia FOREIGN KEY (id_competencia) REFERENCES Competencia (id_competencia),
    CONSTRAINT fk_evidencia_periodo FOREIGN KEY (id_periodo_academico) REFERENCES PeriodoAcademico (id_periodo_academico),
    CONSTRAINT fk_evidencia_autor FOREIGN KEY (id_persona_autor) REFERENCES Persona (id_persona),
    CONSTRAINT fk_evidencia_archivo FOREIGN KEY (id_archivo) REFERENCES ArchivoAdjunto (id_archivo)
) ENGINE=InnoDB;

CREATE TABLE EvidenciaPedagogicaEvaluacion (
    id_evidencia_pedagogica BIGINT NOT NULL,
    id_evaluacion BIGINT NOT NULL,
    PRIMARY KEY (id_evidencia_pedagogica, id_evaluacion),
    CONSTRAINT fk_evidencia_evaluacion_evidencia FOREIGN KEY (id_evidencia_pedagogica)
        REFERENCES EvidenciaPedagogica (id_evidencia_pedagogica),
    CONSTRAINT fk_evidencia_evaluacion_evaluacion FOREIGN KEY (id_evaluacion) REFERENCES Evaluacion (id_evaluacion)
) ENGINE=InnoDB;
