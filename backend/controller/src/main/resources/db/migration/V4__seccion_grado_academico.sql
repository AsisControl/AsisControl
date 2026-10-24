-- Relaciona cada seccion con el grado academico configurado para su año.
-- La columna admite NULL: una seccion puede existir antes de que su grado se habilite.
ALTER TABLE Seccion
    ADD COLUMN id_grado_academico BIGINT NULL;

ALTER TABLE Seccion
    ADD CONSTRAINT fk_seccion_grado_academico
    FOREIGN KEY (id_grado_academico)
    REFERENCES GradoAcademico (id_grado_academico);

CREATE INDEX idx_seccion_grado_academico
    ON Seccion (id_grado_academico);

-- Completa las secciones existentes con el grado de su mismo año y numero.
UPDATE Seccion s
    JOIN AnioAcademico a ON a.anio = s.anio_academico
    JOIN GradoAcademico g ON g.id_anio = a.id_anio_academico AND g.numero = s.grado
SET s.id_grado_academico = g.id_grado_academico
WHERE s.id_grado_academico IS NULL;
