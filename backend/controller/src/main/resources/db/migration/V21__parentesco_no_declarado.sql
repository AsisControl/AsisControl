-- El aprovisionamiento de accesos vincula estudiantes sin inferir parentesco.
-- Se conserva el ENUM existente y se incorpora el valor ya definido en Java.
ALTER TABLE AlumnoApoderado
    MODIFY COLUMN parentesco ENUM ('MADRE','OTRO_FAMILIAR','PADRE','TUTOR_LEGAL','NO_DECLARADO') NOT NULL;
