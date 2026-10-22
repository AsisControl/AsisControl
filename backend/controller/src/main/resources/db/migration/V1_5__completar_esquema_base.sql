-- Completa el esquema base para instalaciones nuevas antes de la migracion V2.
-- Las bases preexistentes se baselinen en la version 2 y conservan sus datos.
-- El contenido refleja las tablas y columnas presentes en el esquema MySQL
-- entregado para la Iteracion 1.

CREATE TABLE AnioAcademico (
    id_anio_academico BIGINT NOT NULL AUTO_INCREMENT,
    anio INTEGER NOT NULL,
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NOT NULL,
    estado ENUM ('BORRADOR','ACTIVO','CERRADO') NOT NULL DEFAULT 'BORRADOR',
    clave_activa INTEGER,
    activo BIT NOT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    admision_abierta BIT NOT NULL DEFAULT 0,
    PRIMARY KEY (id_anio_academico),
    CONSTRAINT uk_anio_numero UNIQUE (anio),
    CONSTRAINT uk_anio_activo UNIQUE (clave_activa)
) ENGINE=InnoDB;

CREATE TABLE PeriodoAcademico (
    id_periodo_academico BIGINT NOT NULL AUTO_INCREMENT,
    id_anio BIGINT NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    orden INTEGER NOT NULL,
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NOT NULL,
    estado ENUM ('PLANIFICADO','ACTIVO','CERRADO') NOT NULL DEFAULT 'PLANIFICADO',
    clave_activa INTEGER,
    activo BIT NOT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id_periodo_academico),
    CONSTRAINT uk_periodo_orden UNIQUE (id_anio, orden),
    CONSTRAINT uk_periodo_activo UNIQUE (clave_activa),
    CONSTRAINT fk_periodo_anio
        FOREIGN KEY (id_anio) REFERENCES AnioAcademico (id_anio_academico)
) ENGINE=InnoDB;

CREATE TABLE GradoAcademico (
    id_grado_academico BIGINT NOT NULL AUTO_INCREMENT,
    id_anio BIGINT NOT NULL,
    numero INTEGER NOT NULL,
    capacidad_default INTEGER NOT NULL,
    activo BIT NOT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id_grado_academico),
    CONSTRAINT uk_grado_anio UNIQUE (id_anio, numero),
    CONSTRAINT fk_grado_anio
        FOREIGN KEY (id_anio) REFERENCES AnioAcademico (id_anio_academico)
) ENGINE=InnoDB;

CREATE TABLE LimiteRecuperacion (
    clave VARCHAR(64) NOT NULL,
    inicio DATETIME(6) NOT NULL,
    cantidad INTEGER NOT NULL,
    PRIMARY KEY (clave)
) ENGINE=InnoDB;

ALTER TABLE RegistroError
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE SeguimientoError (
    id_seguimiento_error BIGINT NOT NULL AUTO_INCREMENT,
    id_registro_error BIGINT NOT NULL,
    actor VARCHAR(150) NOT NULL,
    fecha DATETIME(6) NOT NULL,
    anterior ENUM ('PENDIENTE','EN_SEGUIMIENTO','RESUELTO') NOT NULL,
    nuevo ENUM ('PENDIENTE','EN_SEGUIMIENTO','RESUELTO') NOT NULL,
    observacion VARCHAR(1000) NOT NULL,
    PRIMARY KEY (id_seguimiento_error),
    CONSTRAINT fk_seguimiento_error
        FOREIGN KEY (id_registro_error) REFERENCES RegistroError (id_registro_error)
) ENGINE=InnoDB;

CREATE TABLE TokenRecuperacion (
    id BIGINT NOT NULL AUTO_INCREMENT,
    id_acceso_usuario BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    creado DATETIME(6) NOT NULL,
    expira DATETIME(6) NOT NULL,
    usado DATETIME(6),
    revocado DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_recuperacion_hash UNIQUE (token_hash),
    CONSTRAINT fk_recuperacion_usuario
        FOREIGN KEY (id_acceso_usuario)
        REFERENCES AccesoUsuario (id_acceso_usuario)
) ENGINE=InnoDB;

ALTER TABLE AccesoUsuario
    ADD COLUMN correo_verificado BIT NOT NULL DEFAULT 0,
    ADD COLUMN contrasena_temporal_expira DATETIME(6),
    DROP INDEX uk_acceso_usuario_persona,
    ADD CONSTRAINT uk_acceso_usuario_persona_rol UNIQUE (id_persona, id_rol);

ALTER TABLE SesionAcceso
    ADD COLUMN ultima_actividad DATETIME(6);

UPDATE SesionAcceso
SET ultima_actividad = fecha_creacion
WHERE ultima_actividad IS NULL;

ALTER TABLE SesionAcceso
    MODIFY COLUMN ultima_actividad DATETIME(6) NOT NULL;

ALTER TABLE SolicitudMatricula
    ADD COLUMN anio_academico INTEGER,
    ADD COLUMN grado INTEGER;

UPDATE SolicitudMatricula solicitud
JOIN Seccion seccion ON seccion.id_seccion = solicitud.id_seccion
SET solicitud.anio_academico = seccion.anio_academico,
    solicitud.grado = seccion.grado
WHERE solicitud.anio_academico IS NULL OR solicitud.grado IS NULL;

ALTER TABLE SolicitudMatricula
    MODIFY COLUMN anio_academico INTEGER NOT NULL,
    MODIFY COLUMN grado INTEGER NOT NULL,
    MODIFY COLUMN id_seccion BIGINT NULL;

ALTER TABLE Matricula
    ADD COLUMN clave_vigente INTEGER;

UPDATE Matricula
SET clave_vigente = CASE
    WHEN activo = 1 AND estado_matricula = 'ACTIVA' THEN 1
    ELSE NULL
END;

ALTER TABLE Matricula
    DROP INDEX uk_matricula_alumno_anio,
    ADD CONSTRAINT uk_matricula_vigente
        UNIQUE (id_alumno, anio_academico, clave_vigente);
