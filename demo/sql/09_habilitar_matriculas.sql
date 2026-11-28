-- Se activa el año DESPUES de importar el plan: el flujo normal congela el plan activo.
SELECT (SELECT COUNT(*) FROM Alumno) = 12
   AND (SELECT COUNT(*) FROM Apoderado) = 11
   AND (SELECT COUNT(*) FROM Docente) = 2
   AND (SELECT COUNT(*) FROM Curso WHERE codigo LIKE 'DEMO-%') = 4
   AND (SELECT COUNT(*) FROM Seccion WHERE anio_academico = 2026) = 2
   AND (SELECT COUNT(*) FROM TarifaPension WHERE anio_academico = 2026) = 2
   AND (SELECT COUNT(*) FROM PlanEstudiosCurso) = 4 AS validacion,
   'Completa los Excel 01 a 09 antes de habilitar las matrículas.' AS mensaje;
SELECT COUNT(*) = 0 AS validacion, 'Ya existe otro año activo.' AS mensaje FROM AnioAcademico WHERE estado = 'ACTIVO';
UPDATE AnioAcademico SET estado = 'ACTIVO', clave_activa = 1, admision_abierta = 1,
    fecha_actualizacion = :instante_carga, version = version + 1 WHERE anio = 2026 AND estado = 'BORRADOR';
UPDATE PeriodoAcademico SET estado = 'ACTIVO', clave_activa = 1,
    fecha_actualizacion = :instante_carga, version = version + 1
WHERE id_anio IN (SELECT id_anio_academico FROM AnioAcademico WHERE anio = 2026);
SELECT 'Año 2026 activo. Continúa con los Excel 10 a 12.' AS indicacion;
