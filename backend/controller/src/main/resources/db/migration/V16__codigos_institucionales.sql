-- Códigos institucionales {PREFIJO}{AÑO}{####}: EST, DOC, APO y ADM, con una secuencia por
-- prefijo y año. Los códigos existentes de estudiantes (ESTU-000001) y docentes se conservan;
-- solo reciben código los apoderados y administradores, que hasta ahora no tenían.
CREATE TABLE ContadorCodigoInstitucional (
    prefijo VARCHAR(3) NOT NULL,
    anio INTEGER NOT NULL,
    ultimo_valor BIGINT NOT NULL,
    PRIMARY KEY (prefijo, anio)
) ENGINE=InnoDB;

ALTER TABLE Apoderado ADD COLUMN codigo_apoderado VARCHAR(30) NULL;
ALTER TABLE Administrador ADD COLUMN codigo_administrador VARCHAR(30) NULL;

-- Año de registro del perfil y posición dentro de ese año, en orden de alta.
UPDATE Apoderado a
JOIN (
    SELECT id_apoderado,
           YEAR(fecha_creacion) AS anio,
           ROW_NUMBER() OVER (PARTITION BY YEAR(fecha_creacion) ORDER BY id_apoderado) AS n
    FROM Apoderado
) numerados ON numerados.id_apoderado = a.id_apoderado
SET a.codigo_apoderado = CONCAT('APO', numerados.anio, LPAD(numerados.n, 4, '0'));

UPDATE Administrador a
JOIN (
    SELECT id_administrador,
           YEAR(fecha_creacion) AS anio,
           ROW_NUMBER() OVER (PARTITION BY YEAR(fecha_creacion) ORDER BY id_administrador) AS n
    FROM Administrador
) numerados ON numerados.id_administrador = a.id_administrador
SET a.codigo_administrador = CONCAT('ADM', numerados.anio, LPAD(numerados.n, 4, '0'));

ALTER TABLE Apoderado ADD CONSTRAINT uk_apoderado_codigo UNIQUE (codigo_apoderado);
ALTER TABLE Administrador ADD CONSTRAINT uk_administrador_codigo UNIQUE (codigo_administrador);

-- Las próximas altas continúan después de los códigos asignados arriba.
INSERT INTO ContadorCodigoInstitucional (prefijo, anio, ultimo_valor)
SELECT 'APO', YEAR(fecha_creacion), COUNT(*) FROM Apoderado GROUP BY YEAR(fecha_creacion);

INSERT INTO ContadorCodigoInstitucional (prefijo, anio, ultimo_valor)
SELECT 'ADM', YEAR(fecha_creacion), COUNT(*) FROM Administrador GROUP BY YEAR(fecha_creacion);
