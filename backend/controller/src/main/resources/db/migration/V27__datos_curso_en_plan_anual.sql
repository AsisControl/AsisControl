ALTER TABLE PlanEstudiosCurso ADD COLUMN nombre_plan VARCHAR(120) NULL;
ALTER TABLE PlanEstudiosCurso ADD COLUMN horas_semanales_plan INT NULL;

UPDATE PlanEstudiosCurso
SET nombre_plan = (SELECT c.nombre FROM Curso c WHERE c.id_curso = PlanEstudiosCurso.id_curso),
    horas_semanales_plan = (SELECT c.horas_semanales FROM Curso c WHERE c.id_curso = PlanEstudiosCurso.id_curso);

ALTER TABLE PlanEstudiosCurso MODIFY COLUMN nombre_plan VARCHAR(120) NOT NULL;
ALTER TABLE PlanEstudiosCurso MODIFY COLUMN horas_semanales_plan INT NOT NULL;
