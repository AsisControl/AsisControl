ALTER TABLE GradoAcademico ADD COLUMN plan_definido BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE PlanEstudiosCurso (
    id_grado_academico BIGINT NOT NULL,
    id_curso BIGINT NOT NULL,
    PRIMARY KEY (id_grado_academico, id_curso),
    CONSTRAINT fk_plan_estudios_grado FOREIGN KEY (id_grado_academico)
        REFERENCES GradoAcademico (id_grado_academico),
    CONSTRAINT fk_plan_estudios_curso FOREIGN KEY (id_curso)
        REFERENCES Curso (id_curso)
);

CREATE INDEX idx_plan_estudios_curso ON PlanEstudiosCurso (id_curso);

INSERT INTO PlanEstudiosCurso (id_grado_academico, id_curso)
SELECT c.id_grado_academico, c.id_curso
FROM Curso c WHERE c.id_grado_academico IS NOT NULL AND c.activo = TRUE
UNION
SELECT g.id_grado_academico, ac.id_curso
FROM AsignacionCurso ac
JOIN Curso c ON c.id_curso = ac.id_curso AND c.activo = TRUE
JOIN Seccion s ON s.id_seccion = ac.id_seccion
JOIN AnioAcademico a ON a.anio = s.anio_academico
JOIN GradoAcademico g ON g.id_anio = a.id_anio_academico AND g.numero = s.grado
WHERE ac.activo = TRUE;

UPDATE GradoAcademico
SET plan_definido = TRUE
WHERE id_grado_academico IN (SELECT id_grado_academico FROM PlanEstudiosCurso);
