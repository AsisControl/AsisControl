ALTER TABLE EntregaTarea MODIFY COLUMN nota DECIMAL(14,10) NULL;
ALTER TABLE Tarea ADD COLUMN id_evaluacion_academica BIGINT NULL;
ALTER TABLE Tarea ADD COLUMN puntaje_maximo_fijo BIT(1) NOT NULL DEFAULT 0;
ALTER TABLE Evaluacion ADD COLUMN cuenta_resultado BIT(1) NOT NULL DEFAULT 1;
ALTER TABLE Tarea ADD CONSTRAINT uk_tarea_evaluacion_academica UNIQUE (id_evaluacion_academica);
ALTER TABLE Tarea ADD CONSTRAINT fk_tarea_evaluacion_academica FOREIGN KEY (id_evaluacion_academica) REFERENCES Evaluacion (id_evaluacion);
