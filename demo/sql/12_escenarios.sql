-- EXPO 2026: datos externos para asistencia, evaluaciones, tareas, riesgo y pagos.
-- No ejecuta DDL, no crea claves, no inventa archivos de S3.
-- Requiere Excel 01 a 12. Las notas de los examenes MAT se cargan despues con Excel 13 y 14.

SELECT (SELECT COUNT(*) FROM Matricula WHERE anio_academico = 2026 AND estado_matricula = 'ACTIVA') = 10 AND (SELECT COUNT(*) FROM AsignacionCurso) = 4 AND (SELECT COUNT(*) FROM Horario) = 20 AS validacion, 'Completa los Excel 10 a 12 y sus diez matrículas antes de los escenarios.' AS mensaje;

SELECT (SELECT COUNT(*) FROM Evaluacion) = 0 AND (SELECT COUNT(*) FROM ReglaCalificacion) = 0 AND (SELECT COUNT(*) FROM Tarea) = 0 AS validacion, 'Los escenarios requieren evaluaciones, reglas y tareas vacías.' AS mensaje;

SELECT (SELECT COUNT(*) FROM Alumno a JOIN Persona p ON p.id_persona = a.id_persona WHERE p.numero_documento IN ('88000001','88000006','88000010','88000011','88000012')) = 5 AS validacion, 'No se encontraron los estudiantes del paquete original.' AS mensaje;

SELECT (SELECT COUNT(*) FROM Seccion WHERE anio_academico = 2026 AND grado IN (2,3) AND nombre = 'A') = 2 AS validacion, 'Se requieren las secciones 2 A y 3 A del paquete.' AS mensaje;

-- Reglas y evaluaciones: 60 % examen y 40 % trabajo, por competencia.

INSERT INTO ReglaCalificacion (id_curso, id_grado_academico, numero_version, estado, formula, activo, fecha_creacion, fecha_actualizacion, version) SELECT ac.id_curso, s.id_grado_academico, 1, 'VIGENTE', 'CUALITATIVA_DINAMICA_V1', 1, :instante_carga, :instante_carga, 0 FROM AsignacionCurso ac JOIN Seccion s ON s.id_seccion = ac.id_seccion WHERE s.anio_academico = 2026;

INSERT INTO ReglaCalificacionComponente (id_regla_calificacion, tipo_evaluacion, nombre, peso) SELECT id_regla_calificacion, 'EXAMEN', 'Examen', 60 FROM ReglaCalificacion;

INSERT INTO ReglaCalificacionComponente (id_regla_calificacion, tipo_evaluacion, nombre, peso) SELECT id_regla_calificacion, 'TRABAJO_ACADEMICO', 'Trabajo academico', 40 FROM ReglaCalificacion;

INSERT INTO Evaluacion (id_asignacion_curso, titulo, descripcion, tipo_evaluacion, periodo_evaluacion, estado_evaluacion, fecha_evaluacion, ponderacion_porcentaje, fecha_publicacion, cuenta_resultado, modelo_cualitativo, entrega_por_configurar, activo, fecha_creacion, fecha_actualizacion, version) SELECT ac.id_asignacion_curso, CONCAT('Examen DEMO ', c.codigo), 'Datos ficticios para exposición', 'EXAMEN', 'PERIODO_ACADEMICO', CASE WHEN c.codigo LIKE 'DEMO-MAT-%' THEN 'BORRADOR' ELSE 'PUBLICADA' END, :fecha_referencia, 100, CASE WHEN c.codigo LIKE 'DEMO-MAT-%' THEN NULL ELSE :instante_carga END, 1, 1, 0, 1, :instante_carga, :instante_carga, 0 FROM AsignacionCurso ac JOIN Curso c ON c.id_curso = ac.id_curso JOIN Seccion s ON s.id_seccion = ac.id_seccion WHERE s.anio_academico = 2026;

INSERT INTO Evaluacion (id_asignacion_curso, titulo, descripcion, tipo_evaluacion, periodo_evaluacion, estado_evaluacion, fecha_evaluacion, ponderacion_porcentaje, fecha_publicacion, cuenta_resultado, modelo_cualitativo, entrega_por_configurar, activo, fecha_creacion, fecha_actualizacion, version) SELECT ac.id_asignacion_curso, CONCAT('Trabajo DEMO ', c.codigo), 'Datos ficticios para exposición', 'TRABAJO_ACADEMICO', 'PERIODO_ACADEMICO', 'PUBLICADA', :fecha_referencia, 100, :instante_carga, 1, 1, 0, 1, :instante_carga, :instante_carga, 0 FROM AsignacionCurso ac JOIN Curso c ON c.id_curso = ac.id_curso JOIN Seccion s ON s.id_seccion = ac.id_seccion WHERE s.anio_academico = 2026;

INSERT INTO EvaluacionCompetencia (id_evaluacion, id_competencia) SELECT e.id_evaluacion, co.id_competencia FROM Evaluacion e JOIN AsignacionCurso ac ON ac.id_asignacion_curso = e.id_asignacion_curso JOIN Curso c ON c.id_curso = ac.id_curso JOIN Competencia co ON (c.codigo LIKE 'DEMO-MAT-%' AND co.codigo IN ('DEMO-MAT-CANT','DEMO-MAT-FORMA')) OR (c.codigo LIKE 'DEMO-COM-%' AND co.codigo IN ('DEMO-COM-LEE','DEMO-COM-ESCR'));

INSERT INTO Calificacion (id_evaluacion, id_alumno, id_competencia, nivel_logro, fecha_registro, observacion, activo, fecha_creacion, fecha_actualizacion, version) SELECT e.id_evaluacion, m.id_alumno, ec.id_competencia, CASE WHEN p.numero_documento = '88000010' THEN 'C' ELSE 'A' END, :instante_carga, 'Nivel ficticio del paquete SQL', 1, :instante_carga, :instante_carga, 0 FROM Evaluacion e JOIN EvaluacionCompetencia ec ON ec.id_evaluacion = e.id_evaluacion JOIN AsignacionCurso ac ON ac.id_asignacion_curso = e.id_asignacion_curso JOIN Matricula m ON m.id_seccion = ac.id_seccion JOIN Alumno al ON al.id_alumno = m.id_alumno JOIN Persona p ON p.id_persona = al.id_persona WHERE e.estado_evaluacion = 'PUBLICADA' AND m.estado_matricula = 'ACTIVA';

SELECT (SELECT COUNT(*) FROM SesionClase WHERE fecha IN ('2026-11-13','2026-11-16','2026-11-17','2026-11-18')) = 0 AS validacion, 'Una sesión demo ya existe. Use una instalación limpia para no sobrescribirla.' AS mensaje;

-- Historial real de cuatro sesiones finalizadas por sección; Nicolas tiene cuatro inasistencias.

INSERT INTO SesionClase (id_asignacion_curso, id_horario, fecha, hora_inicio, hora_fin, estado, id_docente_registrador, fecha_hora_finalizacion, activo, fecha_creacion, fecha_actualizacion, version) SELECT ac.id_asignacion_curso, h.id_horario, '2026-11-13', h.hora_inicio, h.hora_fin, 'TOMADA', ac.id_docente, TIMESTAMP '2026-11-13 15:00:00', 1, :instante_carga, :instante_carga, 0 FROM Horario h JOIN AsignacionCurso ac ON ac.id_asignacion_curso = h.id_asignacion_curso JOIN Curso c ON c.id_curso = ac.id_curso WHERE h.dia_semana = 'VIERNES' AND c.codigo LIKE 'DEMO-MAT-%';

INSERT INTO AsistenciaAlumno (id_alumno, id_asignacion_curso, id_docente_registrador, id_sesion_clase, fecha_registro, hora_registro, estado_asistencia, comentario, activo, fecha_creacion, fecha_actualizacion, version) SELECT m.id_alumno, sc.id_asignacion_curso, sc.id_docente_registrador, sc.id_sesion_clase, sc.fecha, TIME '10:05:00', CASE WHEN p.numero_documento = '88000010' THEN 'INASISTENCIA' WHEN p.numero_documento IN ('88000001','88000006') THEN 'PRESENTE' ELSE 'PRESENTE' END, 'Registro ficticio para exposición', 1, :instante_carga, :instante_carga, 0 FROM SesionClase sc JOIN AsignacionCurso ac ON ac.id_asignacion_curso = sc.id_asignacion_curso JOIN Matricula m ON m.id_seccion = ac.id_seccion JOIN Alumno al ON al.id_alumno = m.id_alumno JOIN Persona p ON p.id_persona = al.id_persona WHERE sc.fecha = '2026-11-13' AND m.estado_matricula = 'ACTIVA';

INSERT INTO SesionClase (id_asignacion_curso, id_horario, fecha, hora_inicio, hora_fin, estado, id_docente_registrador, fecha_hora_finalizacion, activo, fecha_creacion, fecha_actualizacion, version) SELECT ac.id_asignacion_curso, h.id_horario, '2026-11-16', h.hora_inicio, h.hora_fin, 'TOMADA', ac.id_docente, TIMESTAMP '2026-11-16 15:00:00', 1, :instante_carga, :instante_carga, 0 FROM Horario h JOIN AsignacionCurso ac ON ac.id_asignacion_curso = h.id_asignacion_curso JOIN Curso c ON c.id_curso = ac.id_curso WHERE h.dia_semana = 'LUNES' AND c.codigo LIKE 'DEMO-MAT-%';

INSERT INTO AsistenciaAlumno (id_alumno, id_asignacion_curso, id_docente_registrador, id_sesion_clase, fecha_registro, hora_registro, estado_asistencia, comentario, activo, fecha_creacion, fecha_actualizacion, version) SELECT m.id_alumno, sc.id_asignacion_curso, sc.id_docente_registrador, sc.id_sesion_clase, sc.fecha, TIME '10:05:00', CASE WHEN p.numero_documento = '88000010' THEN 'INASISTENCIA' WHEN p.numero_documento IN ('88000001','88000006') THEN 'PRESENTE' ELSE 'PRESENTE' END, 'Registro ficticio para exposición', 1, :instante_carga, :instante_carga, 0 FROM SesionClase sc JOIN AsignacionCurso ac ON ac.id_asignacion_curso = sc.id_asignacion_curso JOIN Matricula m ON m.id_seccion = ac.id_seccion JOIN Alumno al ON al.id_alumno = m.id_alumno JOIN Persona p ON p.id_persona = al.id_persona WHERE sc.fecha = '2026-11-16' AND m.estado_matricula = 'ACTIVA';

INSERT INTO SesionClase (id_asignacion_curso, id_horario, fecha, hora_inicio, hora_fin, estado, id_docente_registrador, fecha_hora_finalizacion, activo, fecha_creacion, fecha_actualizacion, version) SELECT ac.id_asignacion_curso, h.id_horario, '2026-11-17', h.hora_inicio, h.hora_fin, 'TOMADA', ac.id_docente, TIMESTAMP '2026-11-17 15:00:00', 1, :instante_carga, :instante_carga, 0 FROM Horario h JOIN AsignacionCurso ac ON ac.id_asignacion_curso = h.id_asignacion_curso JOIN Curso c ON c.id_curso = ac.id_curso WHERE h.dia_semana = 'MARTES' AND c.codigo LIKE 'DEMO-MAT-%';

INSERT INTO AsistenciaAlumno (id_alumno, id_asignacion_curso, id_docente_registrador, id_sesion_clase, fecha_registro, hora_registro, estado_asistencia, comentario, activo, fecha_creacion, fecha_actualizacion, version) SELECT m.id_alumno, sc.id_asignacion_curso, sc.id_docente_registrador, sc.id_sesion_clase, sc.fecha, TIME '10:05:00', CASE WHEN p.numero_documento = '88000010' THEN 'INASISTENCIA' WHEN p.numero_documento IN ('88000001','88000006') THEN 'TARDANZA' ELSE 'PRESENTE' END, 'Registro ficticio para exposición', 1, :instante_carga, :instante_carga, 0 FROM SesionClase sc JOIN AsignacionCurso ac ON ac.id_asignacion_curso = sc.id_asignacion_curso JOIN Matricula m ON m.id_seccion = ac.id_seccion JOIN Alumno al ON al.id_alumno = m.id_alumno JOIN Persona p ON p.id_persona = al.id_persona WHERE sc.fecha = '2026-11-17' AND m.estado_matricula = 'ACTIVA';

INSERT INTO SesionClase (id_asignacion_curso, id_horario, fecha, hora_inicio, hora_fin, estado, id_docente_registrador, fecha_hora_finalizacion, activo, fecha_creacion, fecha_actualizacion, version) SELECT ac.id_asignacion_curso, h.id_horario, '2026-11-18', h.hora_inicio, h.hora_fin, 'TOMADA', ac.id_docente, TIMESTAMP '2026-11-18 15:00:00', 1, :instante_carga, :instante_carga, 0 FROM Horario h JOIN AsignacionCurso ac ON ac.id_asignacion_curso = h.id_asignacion_curso JOIN Curso c ON c.id_curso = ac.id_curso WHERE h.dia_semana = 'MIERCOLES' AND c.codigo LIKE 'DEMO-MAT-%';

INSERT INTO AsistenciaAlumno (id_alumno, id_asignacion_curso, id_docente_registrador, id_sesion_clase, fecha_registro, hora_registro, estado_asistencia, comentario, activo, fecha_creacion, fecha_actualizacion, version) SELECT m.id_alumno, sc.id_asignacion_curso, sc.id_docente_registrador, sc.id_sesion_clase, sc.fecha, TIME '10:05:00', CASE WHEN p.numero_documento = '88000010' THEN 'INASISTENCIA' WHEN p.numero_documento IN ('88000001','88000006') THEN 'INASISTENCIA' ELSE 'PRESENTE' END, 'Registro ficticio para exposición', 1, :instante_carga, :instante_carga, 0 FROM SesionClase sc JOIN AsignacionCurso ac ON ac.id_asignacion_curso = sc.id_asignacion_curso JOIN Matricula m ON m.id_seccion = ac.id_seccion JOIN Alumno al ON al.id_alumno = m.id_alumno JOIN Persona p ON p.id_persona = al.id_persona WHERE sc.fecha = '2026-11-18' AND m.estado_matricula = 'ACTIVA';

-- Sesiones pendientes del día: no pisa las que ya haya materializado el flujo normal.

INSERT INTO SesionClase (id_asignacion_curso, id_horario, fecha, hora_inicio, hora_fin, estado, activo, fecha_creacion, fecha_actualizacion, version) SELECT ac.id_asignacion_curso, h.id_horario, :fecha_referencia, h.hora_inicio, h.hora_fin, 'PENDIENTE', 1, :instante_carga, :instante_carga, 0 FROM Horario h JOIN AsignacionCurso ac ON ac.id_asignacion_curso = h.id_asignacion_curso JOIN Curso c ON c.id_curso = ac.id_curso WHERE c.codigo LIKE 'DEMO-MAT-%' AND h.dia_semana = CASE DAYOFWEEK(:fecha_referencia) WHEN 2 THEN 'LUNES' WHEN 3 THEN 'MARTES' WHEN 4 THEN 'MIERCOLES' WHEN 5 THEN 'JUEVES' WHEN 6 THEN 'VIERNES' ELSE 'NINGUNO' END AND NOT EXISTS (SELECT 1 FROM SesionClase s WHERE s.id_asignacion_curso = ac.id_asignacion_curso AND s.fecha = :fecha_referencia AND s.hora_inicio = h.hora_inicio);

-- Docente: una jornada por día aunque tenga dos clases. La omisión no es inasistencia confirmada.

INSERT INTO JornadaMarcacionDocente (id_docente, fecha, apertura, limite_puntualidad, cierre, activo, fecha_creacion, fecha_actualizacion, version) VALUES ((SELECT d.id_docente FROM Docente d JOIN Persona p ON p.id_persona = d.id_persona WHERE p.numero_documento = '38000001'), '2026-11-13', TIMESTAMP '2026-11-13 07:00:00', TIMESTAMP '2026-11-13 07:45:00', TIMESTAMP '2026-11-13 10:00:00', 1, :instante_carga, :instante_carga, 0);

INSERT INTO AsistenciaDocente (id_docente, fecha_jornada, fecha_hora_marcada, ventana_apertura, limite_puntualidad, ventana_cierre, estado_asistencia, es_contingencia, motivo_contingencia, estado_verificacion, activo, fecha_creacion, fecha_actualizacion, version) VALUES ((SELECT d.id_docente FROM Docente d JOIN Persona p ON p.id_persona = d.id_persona WHERE p.numero_documento = '38000001'), '2026-11-13', TIMESTAMP '2026-11-13 07:30:00', TIMESTAMP '2026-11-13 07:00:00', TIMESTAMP '2026-11-13 07:45:00', TIMESTAMP '2026-11-13 10:00:00', 'PRESENTE', 1, 'Marcación de demostración por SQL; no representa una lectura QR', 'CONFIRMADA', 1, :instante_carga, :instante_carga, 0);

INSERT INTO JornadaMarcacionDocente (id_docente, fecha, apertura, limite_puntualidad, cierre, activo, fecha_creacion, fecha_actualizacion, version) VALUES ((SELECT d.id_docente FROM Docente d JOIN Persona p ON p.id_persona = d.id_persona WHERE p.numero_documento = '38000001'), '2026-11-16', TIMESTAMP '2026-11-16 07:00:00', TIMESTAMP '2026-11-16 07:45:00', TIMESTAMP '2026-11-16 10:00:00', 1, :instante_carga, :instante_carga, 0);

INSERT INTO AsistenciaDocente (id_docente, fecha_jornada, fecha_hora_marcada, ventana_apertura, limite_puntualidad, ventana_cierre, estado_asistencia, es_contingencia, motivo_contingencia, estado_verificacion, activo, fecha_creacion, fecha_actualizacion, version) VALUES ((SELECT d.id_docente FROM Docente d JOIN Persona p ON p.id_persona = d.id_persona WHERE p.numero_documento = '38000001'), '2026-11-16', TIMESTAMP '2026-11-16 07:50:00', TIMESTAMP '2026-11-16 07:00:00', TIMESTAMP '2026-11-16 07:45:00', TIMESTAMP '2026-11-16 10:00:00', 'TARDANZA', 1, 'Marcación de demostración por SQL; no representa una lectura QR', 'CONFIRMADA', 1, :instante_carga, :instante_carga, 0);

INSERT INTO JornadaMarcacionDocente (id_docente, fecha, apertura, limite_puntualidad, cierre, activo, fecha_creacion, fecha_actualizacion, version) VALUES ((SELECT d.id_docente FROM Docente d JOIN Persona p ON p.id_persona = d.id_persona WHERE p.numero_documento = '38000001'), '2026-11-17', TIMESTAMP '2026-11-17 07:00:00', TIMESTAMP '2026-11-17 07:45:00', TIMESTAMP '2026-11-17 10:00:00', 1, :instante_carga, :instante_carga, 0);

-- Tarea libre: no cambia las ponderaciones de las evaluaciones; Mía la entrega en vivo.

INSERT INTO Tarea (nombre, descripcion, id_asignacion_curso, fecha_publicacion, fecha_limite, puntaje_maximo, puntaje_maximo_fijo, max_archivos_entrega, estado_publicacion, modelo_cualitativo, activo, fecha_creacion, fecha_actualizacion, version) SELECT 'DEMO - Resolver un problema cotidiano', 'Explica el procedimiento. Mía presenta la entrega durante la exposición.', ac.id_asignacion_curso, CAST(:fecha_referencia AS DATETIME), TIMESTAMPADD(DAY, 7, CAST(:fecha_referencia AS DATETIME)), 20, 1, 3, 'PUBLICADA', 1, 1, :instante_carga, :instante_carga, 0 FROM AsignacionCurso ac JOIN Curso c ON c.id_curso = ac.id_curso WHERE c.codigo = 'DEMO-MAT-2';

INSERT INTO EntregaTarea (id_tarea, id_alumno, fecha_hora_entrega, respuesta, estado_entrega, activo, fecha_creacion, fecha_actualizacion, version) SELECT id_tarea, (SELECT a.id_alumno FROM Alumno a JOIN Persona p ON p.id_persona = a.id_persona WHERE p.numero_documento = '88000002'), CAST(:fecha_referencia AS DATETIME), 'Respuesta ficticia de ejemplo. El archivo adjunto se sube en vivo y se guarda en S3.', 'ENTREGADO', 1, :instante_carga, :instante_carga, 0 FROM Tarea WHERE nombre = 'DEMO - Resolver un problema cotidiano';

INSERT INTO EntregaTarea (id_tarea, id_alumno, fecha_hora_entrega, respuesta, estado_entrega, activo, fecha_creacion, fecha_actualizacion, version) SELECT id_tarea, (SELECT a.id_alumno FROM Alumno a JOIN Persona p ON p.id_persona = a.id_persona WHERE p.numero_documento = '88000003'), CAST(:fecha_referencia AS DATETIME), 'Respuesta ficticia de ejemplo. El archivo adjunto se sube en vivo y se guarda en S3.', 'ENTREGADO', 1, :instante_carga, :instante_carga, 0 FROM Tarea WHERE nombre = 'DEMO - Resolver un problema cotidiano';

-- Riesgo: alerta ficticia con evidencia de los cuatro registros cargados. Se identifica como SQL de demostracion.

INSERT INTO CriterioRiesgo (nombre, descripcion, indicador, operador, umbral, nivel_riesgo, activo, fecha_creacion, fecha_actualizacion, version) VALUES ('DEMO - Dos inasistencias', 'Nicolás tiene cuatro inasistencias en el período. Recalcular para mostrar la evidencia vigente.', 'INASISTENCIAS', 'MAYOR_IGUAL', 2, 'ALTO', 1, :instante_carga, :instante_carga, 0);

INSERT INTO AlertaRiesgo (id_alumno, id_criterio_riesgo, valor_detectado, nivel_riesgo, estado_alerta, mensaje, observacion, fecha_deteccion, activo, fecha_creacion, fecha_actualizacion, version) SELECT aa.id_alumno, cr.id_criterio_riesgo, COUNT(*), 'ALTO', 'ACTIVA', 'DEMO - Cuatro inasistencias superan el umbral de dos', 'Datos ficticios cargados por SQL para la exposición', :instante_carga, 1, :instante_carga, :instante_carga, 0 FROM AsistenciaAlumno aa CROSS JOIN CriterioRiesgo cr WHERE aa.id_alumno = (SELECT a.id_alumno FROM Alumno a JOIN Persona p ON p.id_persona = a.id_persona WHERE p.numero_documento = '88000010') AND aa.estado_asistencia = 'INASISTENCIA' AND cr.nombre = 'DEMO - Dos inasistencias' GROUP BY aa.id_alumno, cr.id_criterio_riesgo HAVING COUNT(*) >= 2;

INSERT INTO RegistroAuditoria (actor_identificador, accion, modulo, entidad, registro_id, fecha_hora, resultado, motivo, nuevo_valor, correlacion_id) SELECT 'CARGA_SQL_DEMOSTRACION', 'EVALUAR_CRITERIO_RIESGO', 'RIESGO', 'AlertaRiesgo', CONCAT('', ar.id_alerta_riesgo), :instante_carga, 'EXITOSO', 'EN_RIESGO', CONCAT('{"version":1,"idAlumno":', ar.id_alumno, ',"idPeriodo":', (SELECT p.id_periodo_academico FROM PeriodoAcademico p JOIN AnioAcademico a ON a.id_anio_academico = p.id_anio WHERE a.anio = 2026), ',"periodo":"DEMO-2026","idSeccion":', (SELECT id_seccion FROM Seccion WHERE anio_academico = 2026 AND grado = 3), ',"grado":3,"seccion":"A","evaluacion":{"idCriterio":', ar.id_criterio_riesgo, ',"nombreCriterio":"DEMO - Dos inasistencias","nivel":"ALTO","indicador":"INASISTENCIAS","resultado":"EN_RIESGO","valor":', ar.valor_detectado, ',"umbral":2,"operador":"MAYOR_IGUAL","cantidadDatos":4,"minimoDatos":1,"motivo":"Datos ficticios cargados por SQL; se puede recalcular","evidencias":["Matematica: inasistencia 2026-11-13","Matematica: inasistencia 2026-11-16","Matematica: inasistencia 2026-11-17","Matematica: inasistencia 2026-11-18"],"idAlerta":', ar.id_alerta_riesgo, '}}'), 'EXPO-2026-SQL-RIESGO' FROM AlertaRiesgo ar WHERE ar.id_alumno = (SELECT a.id_alumno FROM Alumno a JOIN Persona p ON p.id_persona = a.id_persona WHERE p.numero_documento = '88000010');

-- Última vacante: ambos candidatos solicitan la única plaza restante; ninguno está matriculado todavía.

INSERT INTO SolicitudMatricula (id_alumno, id_seccion, anio_academico, grado, estado_solicitud, fecha_envio, observaciones, activo, fecha_creacion, fecha_actualizacion, version) VALUES ((SELECT a.id_alumno FROM Alumno a JOIN Persona p ON p.id_persona = a.id_persona WHERE p.numero_documento = '88000011'), (SELECT id_seccion FROM Seccion WHERE anio_academico = 2026 AND grado = 2), 2026, 2, 'ENVIADA', :instante_carga, 'DEMO - Candidato a la última vacante', 1, :instante_carga, :instante_carga, 0);

INSERT INTO SolicitudMatricula (id_alumno, id_seccion, anio_academico, grado, estado_solicitud, fecha_envio, observaciones, activo, fecha_creacion, fecha_actualizacion, version) VALUES ((SELECT a.id_alumno FROM Alumno a JOIN Persona p ON p.id_persona = a.id_persona WHERE p.numero_documento = '88000012'), (SELECT id_seccion FROM Seccion WHERE anio_academico = 2026 AND grado = 2), 2026, 2, 'ENVIADA', :instante_carga, 'DEMO - Candidato a la última vacante', 1, :instante_carga, :instante_carga, 0);

-- Próximo año en borrador: la renovación se hace en vivo, al terminar los flujos de 2026.

INSERT INTO Seccion (nombre, grado, anio_academico, id_grado_academico, capacidad_maxima, activo, fecha_creacion, fecha_actualizacion, version) SELECT 'DEMO-A', 4, 2027, g.id_grado_academico, 10, 1, :instante_carga, :instante_carga, 0 FROM GradoAcademico g JOIN AnioAcademico a ON a.id_anio_academico = g.id_anio WHERE a.anio = 2027 AND g.numero = 4;

INSERT INTO TarifaPension (id_concepto_cobro, anio_academico, grado, monto_mensual, activo, fecha_creacion, fecha_actualizacion, version) SELECT id_concepto_cobro, 2027, 4, 120, 1, :instante_carga, :instante_carga, 0 FROM ConceptoCobro WHERE codigo = 'PENSION';

UPDATE Matricula SET resultado_promocion = 'PROMOVIDO', motivo_promocion = 'DEMO - Promoción preparada para renovación 2027', fecha_actualizacion = :instante_carga, version = version + 1 WHERE id_alumno = (SELECT a.id_alumno FROM Alumno a JOIN Persona p ON p.id_persona = a.id_persona WHERE p.numero_documento = '88000006') AND anio_academico = 2026;

-- Finanzas: se reutilizan las obligaciones reales generadas por el importador de matrículas.

SELECT (SELECT COUNT(*) FROM CompromisoPago cp JOIN Matricula m ON m.id_matricula = cp.id_matricula WHERE m.id_alumno = (SELECT a.id_alumno FROM Alumno a JOIN Persona p ON p.id_persona = a.id_persona WHERE p.numero_documento = '88000001') AND cp.mes_pension = 3) = 1 AS validacion, 'Falta la pensión de marzo de Mía. Revise el Excel de matrículas y las tarifas.' AS mensaje;

INSERT INTO TransaccionPago (id_compromiso_pago, id_apoderado, numero_operacion, monto, medio_pago, estado_transaccion, fecha_transaccion, observacion, activo, fecha_creacion, fecha_actualizacion, version) SELECT cp.id_compromiso_pago, ap.id_apoderado, 'DEMO-2026-MIA-MARZO', cp.monto_total, 'EFECTIVO', 'APROBADA', :instante_carga, 'Pago ficticio cargado por SQL para demostrar el historial', 1, :instante_carga, :instante_carga, 0 FROM CompromisoPago cp JOIN Matricula m ON m.id_matricula = cp.id_matricula JOIN Apoderado ap ON ap.id_persona = (SELECT id_persona FROM Persona WHERE numero_documento = '48000001') WHERE m.id_alumno = (SELECT a.id_alumno FROM Alumno a JOIN Persona p ON p.id_persona = a.id_persona WHERE p.numero_documento = '88000001') AND cp.mes_pension = 3;

UPDATE CompromisoPago SET monto_pagado = monto_total, estado_compromiso = 'PAGADO', fecha_actualizacion = :instante_carga, version = version + 1 WHERE id_compromiso_pago IN (SELECT id_compromiso_pago FROM TransaccionPago WHERE numero_operacion = 'DEMO-2026-MIA-MARZO');

INSERT INTO Comprobante (id_transaccion_pago, tipo_comprobante, serie, numero, fecha_emision, monto_total, activo, fecha_creacion, fecha_actualizacion, version) SELECT id_transaccion_pago, 'RECIBO', 'DEMO', 'EXPO-2026-001', :instante_carga, monto, 1, :instante_carga, :instante_carga, 0 FROM TransaccionPago WHERE numero_operacion = 'DEMO-2026-MIA-MARZO';

-- Referencias devueltas a la página: se resuelven por códigos, nunca por IDs fijos.

SELECT 'alumnoMia' AS clave, (SELECT a.id_alumno FROM Alumno a JOIN Persona p ON p.id_persona = a.id_persona WHERE p.numero_documento = '88000001') AS id_recurso;

SELECT 'alumnoDiego' AS clave, (SELECT a.id_alumno FROM Alumno a JOIN Persona p ON p.id_persona = a.id_persona WHERE p.numero_documento = '88000006') AS id_recurso;

SELECT 'alumnoNicolas' AS clave, (SELECT a.id_alumno FROM Alumno a JOIN Persona p ON p.id_persona = a.id_persona WHERE p.numero_documento = '88000010') AS id_recurso;

SELECT 'seccion2' AS clave, (SELECT id_seccion FROM Seccion WHERE anio_academico = 2026 AND grado = 2) AS id_recurso;

SELECT 'seccion3' AS clave, (SELECT id_seccion FROM Seccion WHERE anio_academico = 2026 AND grado = 3) AS id_recurso;

SELECT 'periodo2026' AS clave, (SELECT p.id_periodo_academico FROM PeriodoAcademico p JOIN AnioAcademico a ON a.id_anio_academico = p.id_anio WHERE a.anio = 2026) AS id_recurso;

SELECT 'evaluacionMat2' AS clave, e.id_evaluacion AS id_recurso FROM Evaluacion e JOIN AsignacionCurso ac ON ac.id_asignacion_curso = e.id_asignacion_curso JOIN Curso c ON c.id_curso = ac.id_curso WHERE c.codigo = 'DEMO-MAT-2' AND e.tipo_evaluacion = 'EXAMEN';

SELECT 'evaluacionMat3' AS clave, e.id_evaluacion AS id_recurso FROM Evaluacion e JOIN AsignacionCurso ac ON ac.id_asignacion_curso = e.id_asignacion_curso JOIN Curso c ON c.id_curso = ac.id_curso WHERE c.codigo = 'DEMO-MAT-3' AND e.tipo_evaluacion = 'EXAMEN';

SELECT 'tareaMia' AS clave, id_tarea AS id_recurso FROM Tarea WHERE nombre = 'DEMO - Resolver un problema cotidiano';

SELECT 'Reporte de Mía: 2026-11-13 a 2026-11-18: 2 presentes, 1 tardanza y 1 inasistencia. Rango vacío: 2026-11-06 a 2026-11-07.' AS indicacion;

SELECT 'Docentes: Patricia, 2026-11-13 a 2026-11-17: presente, tardanza y jornada sin marcación.' AS indicacion;

SELECT 'Riesgo: Nicolás tiene una alerta de demostración con cuatro inasistencias. Abre su detalle y recalcula el período DEMO-2026 para mostrar la evaluación vigente.' AS indicacion;

SELECT 'Completa la nota pendiente de Mía, publica Matemática y genera las libretas antes de ocupar la última vacante.' AS indicacion;

SELECT 'Carmen tiene dos hijos: Mía con un pago aprobado y Diego con deuda. Paga la cuota de diciembre de Mía en vivo.' AS indicacion;

SELECT 'La tarea queda publicada sin adjuntos ficticios. Mía puede entregar un archivo real; se guardará usando el almacenamiento configurado.' AS indicacion;

SELECT 'Para mostrar un cruce, intenta mover un bloque al horario que ya ocupa la misma docente o aula.' AS indicacion;
