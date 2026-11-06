-- US-50: cada corrección de una calificación publicada conserva el valor anterior, el motivo,
-- el autor y la fecha. La calificación guarda siempre el nivel vigente.
CREATE TABLE CorreccionCalificacion (
    id_correccion_calificacion BIGINT NOT NULL AUTO_INCREMENT,
    id_calificacion BIGINT NOT NULL,
    numero_version INT NOT NULL,
    nivel_anterior ENUM ('A','AD','B','C') NOT NULL,
    nivel_nuevo ENUM ('A','AD','B','C') NOT NULL,
    motivo VARCHAR(500) NOT NULL,
    autor VARCHAR(250) NOT NULL,
    fecha_correccion DATETIME(6) NOT NULL,
    PRIMARY KEY (id_correccion_calificacion),
    CONSTRAINT uk_correccion_calificacion_version UNIQUE (id_calificacion, numero_version),
    CONSTRAINT fk_correccion_calificacion
        FOREIGN KEY (id_calificacion) REFERENCES Calificacion (id_calificacion)
) ENGINE=InnoDB;
