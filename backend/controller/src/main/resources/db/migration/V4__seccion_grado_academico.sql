-- Relaciona cada seccion con el grado academico configurado para su año.
-- La columna admite NULL: una seccion puede existir antes de que su grado se habilite.
-- Cada paso revisa si el objeto ya existe porque el script manual
-- migrations/004_actualizacion_datos_tablas.sql pudo haberlo creado antes.
SET @existe := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'Seccion'
                  AND COLUMN_NAME = 'id_grado_academico');
SET @sql := IF(@existe = 0,
               'ALTER TABLE Seccion ADD COLUMN id_grado_academico BIGINT NULL',
               'DO 0');
PREPARE paso FROM @sql;
EXECUTE paso;
DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'Seccion'
                  AND CONSTRAINT_NAME = 'fk_seccion_grado_academico');
SET @sql := IF(@existe = 0,
               'ALTER TABLE Seccion ADD CONSTRAINT fk_seccion_grado_academico FOREIGN KEY (id_grado_academico) REFERENCES GradoAcademico (id_grado_academico)',
               'DO 0');
PREPARE paso FROM @sql;
EXECUTE paso;
DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'Seccion'
                  AND INDEX_NAME = 'idx_seccion_grado_academico');
SET @sql := IF(@existe = 0,
               'CREATE INDEX idx_seccion_grado_academico ON Seccion (id_grado_academico)',
               'DO 0');
PREPARE paso FROM @sql;
EXECUTE paso;
DEALLOCATE PREPARE paso;

-- Completa las secciones existentes con el grado de su mismo año y numero.
UPDATE Seccion s
    JOIN AnioAcademico a ON a.anio = s.anio_academico
    JOIN GradoAcademico g ON g.id_anio = a.id_anio_academico AND g.numero = s.grado
SET s.id_grado_academico = g.id_grado_academico
WHERE s.id_grado_academico IS NULL;
