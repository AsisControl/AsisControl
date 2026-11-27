CREATE TABLE SolicitudContingenciaDocente (
    id_solicitud_contingencia_docente BIGINT NOT NULL AUTO_INCREMENT,
    id_docente BIGINT NOT NULL,
    fecha_jornada DATE NOT NULL,
    hora_reportada TIME NOT NULL,
    motivo VARCHAR(1000) NOT NULL,
    estado VARCHAR(12) NOT NULL,
    clave_pendiente INT,
    id_acceso_usuario_resolutor BIGINT,
    fecha_resolucion DATETIME(6),
    motivo_rechazo VARCHAR(1000),
    id_asistencia_docente BIGINT,
    activo BIT NOT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id_solicitud_contingencia_docente),
    CONSTRAINT uk_contingencia_pendiente UNIQUE (id_docente, fecha_jornada, clave_pendiente),
    CONSTRAINT fk_contingencia_docente FOREIGN KEY (id_docente) REFERENCES Docente(id_docente),
    CONSTRAINT fk_contingencia_responsable
        FOREIGN KEY (id_acceso_usuario_resolutor) REFERENCES AccesoUsuario(id_acceso_usuario),
    CONSTRAINT fk_contingencia_marcacion
        FOREIGN KEY (id_asistencia_docente) REFERENCES AsistenciaDocente(id_asistencia_docente)
);
