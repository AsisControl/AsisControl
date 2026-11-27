CREATE TABLE EscalaInstitucional (
    id_escala BIGINT NOT NULL AUTO_INCREMENT,
    numero_version INT NOT NULL,
    ranura VARCHAR(12) NULL,
    fecha_aplicacion DATETIME(6) NULL,
    actor_aplicacion VARCHAR(180) NULL,
    motivo_aplicacion VARCHAR(300) NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_actualizacion DATETIME(6) NOT NULL,
    activo BIT(1) NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id_escala),
    CONSTRAINT uk_escala_numero UNIQUE (numero_version),
    CONSTRAINT uk_escala_ranura UNIQUE (ranura)
);

CREATE TABLE EscalaInstitucionalRango (
    id_escala BIGINT NOT NULL,
    nivel VARCHAR(2) NOT NULL,
    minimo INT NOT NULL,
    maximo INT NOT NULL,
    PRIMARY KEY (id_escala, nivel),
    CONSTRAINT fk_rango_escala FOREIGN KEY (id_escala) REFERENCES EscalaInstitucional (id_escala)
);
