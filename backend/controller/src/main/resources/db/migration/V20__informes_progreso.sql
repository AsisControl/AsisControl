-- US-20: versiones de libretas y copia de los resultados que las componen.
-- Flyway aplica este script una sola vez, despues de V19. No ejecutarlo manualmente.
CREATE TABLE InformeProgreso (
    id_informe_progreso BIGINT NOT NULL AUTO_INCREMENT,
    id_alumno BIGINT NOT NULL,
    id_asignacion_curso BIGINT NOT NULL,
    id_periodo_academico BIGINT NOT NULL,
    numero_version INT NOT NULL DEFAULT 1,
    estado ENUM ('BORRADOR','PUBLICADA') NOT NULL DEFAULT 'BORRADOR',
    id_docente BIGINT NOT NULL,
    fecha_publicacion DATETIME(6) NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    activo BIT(1) NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id_informe_progreso),
    CONSTRAINT uk_informe_alumno_curso_periodo_numero
        UNIQUE (id_alumno, id_asignacion_curso, id_periodo_academico, numero_version),
    INDEX idx_informe_curso_periodo_estado (id_asignacion_curso, id_periodo_academico, estado),
    CONSTRAINT fk_informe_alumno FOREIGN KEY (id_alumno) REFERENCES Alumno (id_alumno),
    CONSTRAINT fk_informe_asignacion FOREIGN KEY (id_asignacion_curso) REFERENCES AsignacionCurso (id_asignacion_curso),
    CONSTRAINT fk_informe_periodo FOREIGN KEY (id_periodo_academico) REFERENCES PeriodoAcademico (id_periodo_academico),
    CONSTRAINT fk_informe_docente FOREIGN KEY (id_docente) REFERENCES Docente (id_docente),
    CONSTRAINT ck_informe_numero_version CHECK (numero_version > 0),
    CONSTRAINT ck_informe_publicacion CHECK (
        (estado = 'BORRADOR' AND fecha_publicacion IS NULL)
        OR (estado = 'PUBLICADA' AND fecha_publicacion IS NOT NULL)
    )
) ENGINE=InnoDB;

CREATE TABLE DetalleInformeProgreso (
    id_detalle_informe_progreso BIGINT NOT NULL AUTO_INCREMENT,
    id_informe_progreso BIGINT NOT NULL,
    id_competencia BIGINT NOT NULL,
    id_evaluacion BIGINT NOT NULL,
    id_calificacion BIGINT NULL,
    nombre_competencia VARCHAR(180) NOT NULL,
    titulo_evaluacion VARCHAR(160) NOT NULL,
    fecha_evaluacion DATE NOT NULL,
    ponderacion_porcentaje DECIMAL(5,2) NOT NULL,
    nivel_logro ENUM ('A','AD','B','C') NULL,
    observacion VARCHAR(1000) NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    activo BIT(1) NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id_detalle_informe_progreso),
    CONSTRAINT uk_detalle_informe_evaluacion_competencia
        UNIQUE (id_informe_progreso, id_evaluacion, id_competencia),
    CONSTRAINT fk_detalle_informe FOREIGN KEY (id_informe_progreso) REFERENCES InformeProgreso (id_informe_progreso),
    CONSTRAINT fk_detalle_competencia FOREIGN KEY (id_competencia) REFERENCES Competencia (id_competencia),
    CONSTRAINT fk_detalle_evaluacion FOREIGN KEY (id_evaluacion) REFERENCES Evaluacion (id_evaluacion),
    CONSTRAINT fk_detalle_calificacion FOREIGN KEY (id_calificacion) REFERENCES Calificacion (id_calificacion)
) ENGINE=InnoDB;
