-- Mantiene los datos e índices existentes. Permite fichas sin documento/contacto.
ALTER TABLE Persona MODIFY apellido_materno VARCHAR(80) NULL;
ALTER TABLE Persona MODIFY tipo_documento ENUM('DNI','PASAPORTE','CARNET_EXTRANJERIA') NULL;
ALTER TABLE Persona MODIFY numero_documento VARCHAR(20) NULL;
ALTER TABLE Persona MODIFY sexo ENUM('FEMENINO','MASCULINO','OTRO','PREFIERE_NO_INDICAR') NULL;
ALTER TABLE Persona MODIFY correo VARCHAR(150) NULL;

CREATE TABLE ContadorCodigoAlumno (
    id_contador INTEGER NOT NULL PRIMARY KEY,
    ultimo_valor BIGINT NOT NULL
) ENGINE=InnoDB;

INSERT INTO ContadorCodigoAlumno (id_contador, ultimo_valor)
SELECT 1, COALESCE(MAX(CAST(SUBSTRING(codigo_alumno, 6) AS UNSIGNED)), 0)
FROM Alumno WHERE codigo_alumno REGEXP '^ESTU-[0-9]+$';
