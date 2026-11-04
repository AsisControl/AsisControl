-- asistencia alumnos
-- HU14: la asistencia de alumnos se registra por sesion de clase (encuentro programado).
-- SesionClase materializa cada encuentro del anio academico; las horas se copian para
-- conservar como estaba programada la sesion aunque el horario cambie despues.

CREATE TABLE SesionClase (
    id_sesion_clase BIGINT NOT NULL AUTO_INCREMENT,
    activo BIT NOT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    id_asignacion_curso BIGINT NOT NULL,
    id_horario BIGINT NOT NULL,
    fecha DATE NOT NULL,
    hora_inicio TIME(0) NOT NULL,
    hora_fin TIME(0) NOT NULL,
    estado ENUM ('PENDIENTE','BORRADOR','TOMADA') NOT NULL,
    id_docente_registrador BIGINT NULL,
    fecha_hora_finalizacion DATETIME(6) NULL,
    PRIMARY KEY (id_sesion_clase),
    CONSTRAINT uk_sesion_clase_encuentro
        UNIQUE (id_asignacion_curso, fecha, hora_inicio),
    CONSTRAINT fk_sesion_clase_asignacion
        FOREIGN KEY (id_asignacion_curso) REFERENCES AsignacionCurso (id_asignacion_curso),
    CONSTRAINT fk_sesion_clase_horario
        FOREIGN KEY (id_horario) REFERENCES Horario (id_horario),
    CONSTRAINT fk_sesion_clase_docente
        FOREIGN KEY (id_docente_registrador) REFERENCES Docente (id_docente),
    INDEX idx_sesion_clase_estado_fecha (estado, fecha)
) ENGINE=InnoDB;

CREATE TABLE CorreccionAsistencia (
    id_correccion_asistencia BIGINT NOT NULL AUTO_INCREMENT,
    activo BIT NOT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    id_sesion_clase BIGINT NOT NULL,
    id_docente BIGINT NOT NULL,
    motivo VARCHAR(500) NOT NULL,
    fecha_hora DATETIME(6) NOT NULL,
    PRIMARY KEY (id_correccion_asistencia),
    CONSTRAINT fk_correccion_asistencia_sesion
        FOREIGN KEY (id_sesion_clase) REFERENCES SesionClase (id_sesion_clase),
    CONSTRAINT fk_correccion_asistencia_docente
        FOREIGN KEY (id_docente) REFERENCES Docente (id_docente)
) ENGINE=InnoDB;

CREATE TABLE CorreccionAsistenciaDetalle (
    id_correccion_asistencia_detalle BIGINT NOT NULL AUTO_INCREMENT,
    id_correccion_asistencia BIGINT NOT NULL,
    id_alumno BIGINT NOT NULL,
    estado_anterior ENUM ('INASISTENCIA','INASISTENCIA_JUSTIFICADA','PRESENTE','SIN_MARCACION','TARDANZA','TARDANZA_JUSTIFICADA') NOT NULL,
    estado_nuevo ENUM ('INASISTENCIA','INASISTENCIA_JUSTIFICADA','PRESENTE','SIN_MARCACION','TARDANZA','TARDANZA_JUSTIFICADA') NOT NULL,
    PRIMARY KEY (id_correccion_asistencia_detalle),
    CONSTRAINT uk_correccion_detalle_alumno
        UNIQUE (id_correccion_asistencia, id_alumno),
    CONSTRAINT fk_correccion_detalle_correccion
        FOREIGN KEY (id_correccion_asistencia) REFERENCES CorreccionAsistencia (id_correccion_asistencia),
    CONSTRAINT fk_correccion_detalle_alumno
        FOREIGN KEY (id_alumno) REFERENCES Alumno (id_alumno)
) ENGINE=InnoDB;

-- Cada registro de alumno pertenece a una sesion. La tabla no tiene registros, asi que
-- la columna se agrega nullable solo para crear la FK y la unicidad, y al final pasa a NOT NULL.
ALTER TABLE AsistenciaAlumno
    ADD COLUMN id_sesion_clase BIGINT NULL;

ALTER TABLE AsistenciaAlumno
    ADD CONSTRAINT fk_asistencia_alumno_sesion
    FOREIGN KEY (id_sesion_clase) REFERENCES SesionClase (id_sesion_clase);

-- La nueva unicidad se crea antes de quitar la anterior: ambas empiezan por id_alumno
-- y MySQL necesita un indice que respalde la llave foranea del alumno.
ALTER TABLE AsistenciaAlumno
    ADD CONSTRAINT uk_asistencia_alumno_sesion UNIQUE (id_alumno, id_sesion_clase);

-- Dos encuentros separados el mismo dia son sesiones distintas, por eso deja de
-- valer la unicidad por alumno, asignacion y fecha.
ALTER TABLE AsistenciaAlumno
    DROP INDEX uk_asistencia_alumno_fecha;

-- Con NOT NULL la unicidad (id_alumno, id_sesion_clase) protege todos los registros.
ALTER TABLE AsistenciaAlumno
    MODIFY COLUMN id_sesion_clase BIGINT NOT NULL;
