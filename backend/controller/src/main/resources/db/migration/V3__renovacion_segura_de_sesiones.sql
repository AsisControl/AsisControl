-- Se conserva el historial de hashes para detectar reutilización de tokens rotados.
CREATE TABLE TokenRenovacion (
    id_token_renovacion BIGINT NOT NULL AUTO_INCREMENT,
    id_sesion_acceso BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    fecha_creacion DATETIME(6) NOT NULL,
    fecha_uso DATETIME(6),
    PRIMARY KEY (id_token_renovacion),
    CONSTRAINT uk_renovacion_hash UNIQUE (token_hash),
    CONSTRAINT fk_renovacion_sesion FOREIGN KEY (id_sesion_acceso)
        REFERENCES SesionAcceso (id_sesion_acceso)
) ENGINE=InnoDB;
