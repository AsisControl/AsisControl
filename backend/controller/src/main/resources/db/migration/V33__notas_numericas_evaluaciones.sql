ALTER TABLE Calificacion ADD COLUMN nota_numerica DECIMAL(12,10) NULL;
ALTER TABLE Evaluacion ADD COLUMN id_escala BIGINT NULL;
ALTER TABLE Evaluacion ADD CONSTRAINT fk_evaluacion_escala FOREIGN KEY (id_escala) REFERENCES EscalaInstitucional (id_escala);
ALTER TABLE CorreccionCalificacion ADD COLUMN nota_anterior DECIMAL(12,10) NULL;
ALTER TABLE CorreccionCalificacion ADD COLUMN nota_nueva DECIMAL(12,10) NULL;
