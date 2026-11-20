# Reportes institucionales de asistencia

## Alcance

US-16 / ASI-3, tareas de backend, vistas de estudiantes y docentes y pruebas. La pantalla vive en Asistencia del administrador, pestaña Reportes de asistencia. No incorpora las pestañas de riesgo, evaluaciones o boletas de Académico, ni exportaciones que US-16 no solicita.

## Cálculo y consulta

`GET /api/reportes/asistencia` recibe `tipo=ESTUDIANTES|DOCENTES`, `periodoId`, `seccionId`, `personaId`, `busqueda`, `desde`, `hasta`, `estado`, `pagina` y `tamano`. El tamaño máximo es 100. `/filtros` entrega períodos identificados por su ID y secciones con año y grado.

Se exige simultáneamente rol ADMINISTRADOR y permiso ASISTENCIA_LEER. El backend valida rango de fechas, paginación, período, sección y longitud de búsqueda; todos los valores se envían como parámetros SQL.

Los indicadores se calculan sobre todos los registros del filtro. El estado seleccionado limita solo el detalle, conservando el denominador del resumen. Porcentaje = cantidad del estado / total del filtro × 100, redondeado a dos decimales. El redondeo puede hacer que la suma visible difiera de 100 por una centésima. Un filtro sin registros devuelve indicadores y filas vacíos.

Estudiantes: una observación por alumno y sesión finalizada TOMADA, excluyendo borradores y registros inactivos. La hora mostrada es de registro, no de llegada. TARDANZA_JUSTIFICADA e INASISTENCIA_JUSTIFICADA se agrupan con su estado base, conservando la distinción en cada fila. La consulta utiliza la asistencia vigente y no suma las filas de auditoría de US-32.

Docentes: una observación por jornada, no por clase. El filtro de sección utiliza la existencia de una sesión del docente ese día en la sección; no multiplica la marcación por la cantidad de clases. Las marcaciones anteriores conservan su estado guardado. Una jornada cerrada sin marcación activa genera un detalle SIN_MARCACION a partir de su fotografía persistida, no una inasistencia confirmada.

## Regla acordada para las nuevas marcaciones QR

La jornada se obtiene de las clases programadas del docente en la fecha del reloj institucional America/Lima. Para este proyecto, la primera y última clase son una simplificación de las actividades laborales; reuniones externas a ese calendario no se incluyen.

- Abre 60 minutos antes del inicio de la primera clase.
- Hasta 15 minutos antes de ese inicio, inclusive, se registra PRESENTE.
- Después se registra TARDANZA mientras no termine la última clase.
- El cierre es exclusivo: a la hora exacta de fin ya no se acepta QR y se solicita revisión administrativa.
- Sin clases programadas, el QR no asigna un estado arbitrario: solicita revisión administrativa.
- La vigencia del QR y el control de duplicados siguen aplicándose.

Ejemplo: primera clase 09:00 y última clase termina 17:00. Ventana [08:00, 17:00), puntual hasta 08:45 inclusive. No es una tolerancia de 15 minutos después de la clase; es anticipación obligatoria acordada para este proyecto, no una regla nacional.

## Persistencia y límites históricos

V24 agrega las columnas ventana_apertura, limite_puntualidad y ventana_cierre a AsistenciaDocente. Son nulas en los registros anteriores y no se recalculan retroactivamente. El flujo de contingencia conserva la asignación administrativa existente y su motivo.

V24 también crea JornadaMarcacionDocente. Un job, iniciado 10 segundos después del arranque y repetido cada minuto, guarda la ventana del día para docentes activos con clases activas. No reconstruye días anteriores ni crea una jornada si el servidor arrancó después de su cierre. Una vez guardada, la fotografía de esa jornada se conserva. Las modificaciones posteriores del horario no deben interpretarse como una reconstrucción del control histórico.

No todos los días sin marcación histórica son faltas. Antes de V24, o si el sistema no estuvo operativo durante una jornada, puede no existir evidencia para calcular su omisión. El reporte lo explica en pantalla. Una marcación activa registrada por contingencia elimina la omisión del informe sin duplicar la jornada.

Si ya existe una fotografía de jornada, la marcación QR usa esos mismos límites, evitando que un cambio posterior de horario altere silenciosamente su regla. Una modificación excepcional de la jornada ya guardada requiere revisión administrativa. En el perfil test se deshabilita el job periódico; su prueba lo invoca expresamente con un reloj fijo para no interferir con otros escenarios.

## Correcciones existentes

US-32 permite al administrador con ASISTENCIA_ESCRIBIR corregir un registro de alumno de una sesión TOMADA desde su detalle en el reporte. La entrada administrativa muestra únicamente al estudiante seleccionado, localizado por el ID de su asistencia, y envía solo su cambio. Si el registro ya no corresponde a la sesión, no abre una edición alternativa. Se reutiliza el formulario de corrección, con motivo obligatorio, validación de estados y alumnos existentes y control de versión. El docente conserva la edición por sesión. El registro inicial y la finalización siguen reservados al docente titular. Esta ampliación no agrega una corrección de marcaciones docentes.

V23 agrega actor_identificador, actor_nombre y actor_rol a CorreccionAsistencia. Las nuevas correcciones identifican al autor real; el campo docente conserva el contexto del docente de la clase. El historial anterior permanece sin reinterpretación. La auditoría general también conserva la identidad autenticada.

## Verificación local

Realizar respaldo antes de arrancar contra MySQL. Flyway aplica V23 y V24 pendientes al iniciar con perfil mysql; no ejecutar el SQL manualmente ni editar migraciones ya aplicadas. Los tests HTTP usan H2 aislado y no modifican la base de desarrollo ni RDS.

Las pruebas agregadas cubren filtros, vacíos, denominador independiente de la paginación, detalle por estado, agrupación de justificaciones, permisos, corrección US-32, jornadas sin marcar, resolución por contingencia y límites exactos de apertura, puntualidad y cierre. La prueba de integración de corrección usa el endpoint existente de US-32.

Archivos principales: VentanaMarcacionDocente, AsistenciaDocenteService, JornadasMarcacionService, ReporteAsistenciaController, ReporteAsistenciaService, ReporteAsistenciaDtos y frontend/src/modules/attendance/reports. La pantalla reutiliza TabNav, PanelHeading, Feedback, Loading, StatusPill, Dialog y Pager del sistema.
