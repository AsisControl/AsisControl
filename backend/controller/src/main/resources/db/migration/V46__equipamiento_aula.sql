-- ASISCTRL-241: equipamiento del aula en texto libre; nulo significa sin equipamiento.
ALTER TABLE Aula ADD COLUMN equipamiento VARCHAR(500) NULL;
