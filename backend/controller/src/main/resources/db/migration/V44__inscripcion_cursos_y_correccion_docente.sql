-- inscripcion cursos: cursos del plan en los que queda inscrita cada matrícula.
CREATE TABLE InscripcionCurso (
    id_inscripcion_curso BIGINT NOT NULL AUTO_INCREMENT,
    id_matricula BIGINT NOT NULL,
    id_curso BIGINT NOT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    activo BIT(1) NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id_inscripcion_curso),
    CONSTRAINT uk_inscripcion_matricula_curso UNIQUE (id_matricula, id_curso),
    CONSTRAINT fk_inscripcion_matricula FOREIGN KEY (id_matricula) REFERENCES Matricula (id_matricula),
    CONSTRAINT fk_inscripcion_curso FOREIGN KEY (id_curso) REFERENCES Curso (id_curso)
);

CREATE INDEX ix_inscripcion_curso ON InscripcionCurso (id_curso);

-- Carga inicial: las matrículas activas y finalizadas quedan inscritas en el plan de su año y grado.
INSERT INTO InscripcionCurso (id_matricula, id_curso, fecha_creacion, fecha_actualizacion, activo, version)
SELECT m.id_matricula, p.id_curso, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 1, 0
FROM Matricula m
JOIN Seccion s ON s.id_seccion = m.id_seccion
JOIN AnioAcademico a ON a.anio = s.anio_academico
JOIN GradoAcademico g ON g.id_anio = a.id_anio_academico AND g.numero = s.grado AND g.plan_definido = 1
JOIN PlanEstudiosCurso p ON p.id_grado_academico = g.id_grado_academico
JOIN Curso c ON c.id_curso = p.id_curso AND c.activo = 1
WHERE m.activo = 1 AND m.estado_matricula IN ('ACTIVA', 'FINALIZADA');

-- correccion marcacion docente: valor anterior y nuevo de cada corrección del administrador.
CREATE TABLE CorreccionAsistenciaDocente (
    id_correccion_asistencia_docente BIGINT NOT NULL AUTO_INCREMENT,
    id_asistencia_docente BIGINT NOT NULL,
    hora_anterior DATETIME(6) NOT NULL,
    hora_nueva DATETIME(6) NOT NULL,
    estado_anterior VARCHAR(35) NOT NULL,
    estado_nuevo VARCHAR(35) NOT NULL,
    motivo VARCHAR(500) NOT NULL,
    actor_identificador VARCHAR(100) NOT NULL,
    actor_nombre VARCHAR(300),
    fecha_hora DATETIME(6) NOT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    activo BIT(1) NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id_correccion_asistencia_docente),
    CONSTRAINT fk_correccion_docente_asistencia FOREIGN KEY (id_asistencia_docente)
        REFERENCES AsistenciaDocente (id_asistencia_docente)
);

CREATE INDEX ix_correccion_docente_asistencia ON CorreccionAsistenciaDocente (id_asistencia_docente, fecha_hora);
