-- EXPO 2026. Ejecutar desde Administracion > Preparar demostracion.
-- Los parametros de fecha los proporciona el ejecutor; no contiene claves.
SELECT (SELECT COUNT(*) FROM AnioAcademico) = 0
   AND (SELECT COUNT(*) FROM Alumno) = 0
   AND (SELECT COUNT(*) FROM Apoderado) = 0
   AND (SELECT COUNT(*) FROM Docente) = 0 AS validacion,
   'Este paquete requiere una instalación limpia con solo el administrador inicial.' AS mensaje;
SELECT COUNT(*) = 1 AS validacion, 'Falta el concepto PENSION del catálogo inicial.' AS mensaje
FROM ConceptoCobro WHERE codigo = 'PENSION' AND es_pension = 1 AND activo = 1;

INSERT INTO AnioAcademico (anio, nombre_plan_estudios, fecha_inicio, fecha_fin, estado, admision_abierta, activo, fecha_creacion, fecha_actualizacion, version)
VALUES (2026, 'Plan DEMO 2026', '2026-01-01', '2026-12-31', 'BORRADOR', 0, 1, :instante_carga, :instante_carga, 0),
       (2027, 'Plan DEMO 2027', '2027-01-01', '2027-12-31', 'BORRADOR', 0, 1, :instante_carga, :instante_carga, 0);
INSERT INTO PeriodoAcademico (id_anio, nombre, orden, fecha_inicio, fecha_fin, estado, activo, fecha_creacion, fecha_actualizacion, version)
SELECT id_anio_academico, CONCAT('DEMO-', anio), 1, fecha_inicio, fecha_fin, 'PLANIFICADO', 1, :instante_carga, :instante_carga, 0
FROM AnioAcademico WHERE anio IN (2026, 2027);
INSERT INTO GradoAcademico (id_anio, numero, capacidad_default, plan_definido, activo, fecha_creacion, fecha_actualizacion, version)
SELECT id_anio_academico, 2, 6, 0, 1, :instante_carga, :instante_carga, 0 FROM AnioAcademico WHERE anio = 2026;
INSERT INTO GradoAcademico (id_anio, numero, capacidad_default, plan_definido, activo, fecha_creacion, fecha_actualizacion, version)
SELECT id_anio_academico, 3, 10, 0, 1, :instante_carga, :instante_carga, 0 FROM AnioAcademico WHERE anio = 2026;
INSERT INTO GradoAcademico (id_anio, numero, capacidad_default, plan_definido, activo, fecha_creacion, fecha_actualizacion, version)
SELECT id_anio_academico, 4, 10, 0, 1, :instante_carga, :instante_carga, 0 FROM AnioAcademico WHERE anio = 2027;

SELECT 'anio2026' AS clave, id_anio_academico AS id_recurso FROM AnioAcademico WHERE anio = 2026;
SELECT 'anio2027' AS clave, id_anio_academico AS id_recurso FROM AnioAcademico WHERE anio = 2027;
SELECT 'periodo2026' AS clave, p.id_periodo_academico AS id_recurso FROM PeriodoAcademico p JOIN AnioAcademico a ON a.id_anio_academico = p.id_anio WHERE a.anio = 2026;
SELECT 'Configuración lista. Continúa con los Excel 01 a 09.' AS indicacion;
