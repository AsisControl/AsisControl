-- Identidad del autor real de cada nueva corrección; no reescribe el historial previo.
ALTER TABLE CorreccionAsistencia
    ADD COLUMN actor_identificador VARCHAR(100) NULL,
    ADD COLUMN actor_nombre VARCHAR(300) NULL,
    ADD COLUMN actor_rol VARCHAR(30) NULL;
