-- Solo registra cargas solicitadas explícitamente; esta migración no crea datos demo.
CREATE TABLE CargaDemostracion (
    id_carga_demostracion BIGINT NOT NULL AUTO_INCREMENT,
    paquete VARCHAR(50) NOT NULL,
    etapa VARCHAR(30) NOT NULL,
    fecha_referencia DATE NOT NULL,
    fecha_ejecucion DATETIME(6) NOT NULL,
    PRIMARY KEY (id_carga_demostracion),
    CONSTRAINT uk_carga_demo_etapa UNIQUE (paquete, etapa)
);
CREATE TABLE CargaDemostracionRecurso (
    id_carga_demostracion BIGINT NOT NULL,
    clave VARCHAR(100) NOT NULL,
    id_recurso BIGINT NOT NULL,
    PRIMARY KEY (id_carga_demostracion, clave),
    CONSTRAINT fk_carga_demo_recurso FOREIGN KEY (id_carga_demostracion)
        REFERENCES CargaDemostracion (id_carga_demostracion)
);
