-- Contexto academico del curso: el grado de un anio (GradoAcademico pertenece a un AnioAcademico).
-- Admite NULL para los cursos registrados antes de esta version; los nuevos lo exigen en el backend.
ALTER TABLE Curso
    ADD COLUMN id_grado_academico BIGINT NULL;

ALTER TABLE Curso
    ADD CONSTRAINT fk_curso_grado_academico
    FOREIGN KEY (id_grado_academico)
    REFERENCES GradoAcademico (id_grado_academico);

CREATE INDEX idx_curso_grado_academico
    ON Curso (id_grado_academico);
