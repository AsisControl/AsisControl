-- ASISCTRL-180: el punto de marcacion es un QR de la institucion; el docente se identifica
-- con su sesion al escanear, por eso el codigo ya no pertenece a un docente ni se consume.
ALTER TABLE CodigoQRAsistencia MODIFY COLUMN id_docente BIGINT NULL;

-- La llave foranea del codigo necesita un indice propio antes de quitar la unicidad.
CREATE INDEX idx_asistencia_docente_codigo_qr ON AsistenciaDocente (id_codigo_qr);
ALTER TABLE AsistenciaDocente DROP INDEX uk_asistencia_docente_qr;
