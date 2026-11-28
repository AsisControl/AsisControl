CREATE TABLE PlanEstudiosCursoCompetencia (
    id_grado_academico BIGINT NOT NULL,
    id_curso BIGINT NOT NULL,
    id_competencia BIGINT NOT NULL,
    PRIMARY KEY (id_grado_academico, id_curso, id_competencia),
    CONSTRAINT fk_competencia_plan_grado FOREIGN KEY (id_grado_academico)
        REFERENCES GradoAcademico (id_grado_academico),
    CONSTRAINT fk_competencia_plan_curso FOREIGN KEY (id_curso) REFERENCES Curso (id_curso),
    CONSTRAINT fk_competencia_plan_catalogo FOREIGN KEY (id_competencia) REFERENCES Competencia (id_competencia)
);

INSERT INTO PlanEstudiosCursoCompetencia (id_grado_academico, id_curso, id_competencia)
SELECT plan.id_grado_academico, plan.id_curso, vinculo.id_competencia
FROM PlanEstudiosCurso plan
INNER JOIN CursoCompetencia vinculo ON vinculo.id_curso = plan.id_curso
INNER JOIN Competencia competencia ON competencia.id_competencia = vinculo.id_competencia
WHERE vinculo.activo = TRUE AND competencia.activo = TRUE;
