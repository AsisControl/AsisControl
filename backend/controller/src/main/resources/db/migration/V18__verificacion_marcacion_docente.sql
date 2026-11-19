-- ASISCTRL-180: la marcacion del docente se cruza con la asistencia de alumnos de su sesion.
-- Las marcaciones existentes no tienen sesion asociada y se consideran confirmadas.
ALTER TABLE AsistenciaDocente
    ADD COLUMN id_sesion_clase BIGINT NULL,
    ADD COLUMN estado_verificacion ENUM ('CONFIRMADA','OBSERVADA') NOT NULL DEFAULT 'CONFIRMADA';

ALTER TABLE AsistenciaDocente
    ADD CONSTRAINT fk_asistencia_docente_sesion
    FOREIGN KEY (id_sesion_clase) REFERENCES SesionClase (id_sesion_clase);

CREATE INDEX idx_asistencia_docente_sesion ON AsistenciaDocente (id_sesion_clase);
CREATE INDEX idx_asistencia_docente_verificacion ON AsistenciaDocente (estado_verificacion, fecha_jornada);
