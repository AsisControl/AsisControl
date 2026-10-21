
ALTER TABLE AnioAcademico
    MODIFY COLUMN estado
        ENUM('BORRADOR', 'ACTIVO', 'CERRADO')
        NOT NULL DEFAULT 'BORRADOR';

ALTER TABLE PeriodoAcademico
    MODIFY COLUMN estado
        ENUM('PLANIFICADO', 'ACTIVO', 'CERRADO')
        NOT NULL DEFAULT 'PLANIFICADO';

CREATE TABLE DiaNoLectivo (
    id_dia_no_lectivo BIGINT NOT NULL AUTO_INCREMENT,

    id_anio BIGINT NOT NULL,

    fecha DATE NOT NULL,

    descripcion VARCHAR(255) NULL,

    fecha_creacion DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6),

    fecha_actualizacion DATETIME(6) NULL
        DEFAULT NULL
        ON UPDATE CURRENT_TIMESTAMP(6),

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_dia_no_lectivo
        PRIMARY KEY (id_dia_no_lectivo),

    CONSTRAINT fk_dia_no_lectivo_anio
        FOREIGN KEY (id_anio)
        REFERENCES AnioAcademico(id_anio_academico)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,

    CONSTRAINT uk_dia_no_lectivo_anio_fecha
        UNIQUE (id_anio, fecha),

    INDEX idx_dia_no_lectivo_fecha (fecha)
) ENGINE=InnoDB;
