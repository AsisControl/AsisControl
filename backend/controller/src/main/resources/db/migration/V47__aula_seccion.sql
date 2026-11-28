-- ASISCTRL-240: aula opcional de una sección; varias secciones pueden compartirla.
ALTER TABLE Seccion ADD COLUMN id_aula BIGINT NULL;
ALTER TABLE Seccion ADD CONSTRAINT fk_seccion_aula FOREIGN KEY (id_aula) REFERENCES Aula (id_aula);
