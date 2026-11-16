# US-20: PDF de progreso, primera etapa

Genera un PDF al solicitarlo, sin almacenar informes ni crear entidades. Los datos
corresponden al momento de la consulta. No conserva versiones anteriores.

## Descarga

`GET /api/calificaciones/reporte-avance?idAlumno=1&idAsignacionCurso=2&idPeriodoAcademico=3`

Los identificadores son ejemplos: usar los existentes en la base de datos.
Enviar la autenticacion habitual del API. La respuesta es `application/pdf`,
con nombre de descarga y `Cache-Control: no-store`.

Esta etapa admite docentes titulares del curso y administradores con
`CALIFICACIONES_LEER`. El flujo de publicacion y el acceso de alumnos/apoderados
quedan pendientes; no se modifica su acceso actual.

El alumno debe tener matricula activa o finalizada en la seccion. El periodo
academico debe estar activo (no eliminado), pertenecer al año de la seccion y
se filtra por fechas inclusivas de evaluacion. No confundir el ID del periodo
academico con el enum PeriodoEvaluacion (BIMESTRAL, MENSUAL, etc.).

Se incluyen calificaciones activas de evaluaciones activas publicadas o cerradas.
Sin notas disponibles se devuelve un error de negocio, no un PDF vacio.

## Contenido

Alumno y codigo, curso, grado, seccion, año, docente, periodo y fechas.
Detalle ordenado por competencia, fecha de evaluacion e ID de calificacion:
competencia, evaluacion, fecha, nivel registrado, ponderacion y observacion.
Las observaciones vacias se muestran como "Sin observaciones". La ponderacion
es la registrada en cada evaluacion; no se renormaliza al conjunto del informe.
La fecha de generacion usa America/Lima.

El consolidado por competencia y las referencias de evidencia quedan pendientes
de definir sus reglas. No se convierten niveles a numeros ni se infieren evidencias.
La pantalla y el boton de descarga se integraran en una etapa posterior.

## Organizacion

- DTO: ReporteAvanceDto.
- Preparacion de datos: ReporteAvanceService (transaccion de lectura).
- Renderizado fuera de la transaccion: GeneradorReporteAvance.
- Plantilla editable: service/src/main/resources/reports/reporte-avance.jrxml.
- Endpoint: CalificacionController.

## Verificacion

`./mvnw.cmd -pl controller -am test "-Dtest=ReporteAvanceTest,ReporteAvanceHttpTest,CalificacionServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Las pruebas cubren limites de fechas, año incorrecto, alumno ajeno, docente ajeno,
ausencia de notas y generacion real de un PDF con varias paginas y observaciones
largas. El PDF sintetico se escribe en target/reportes-prueba/avance.pdf respecto
al directorio de trabajo de las pruebas (controller con ejecucion bifurcada;
backend al ejecutar Maven desde backend con forkCount=0).

La prueba HTTP usa datos persistidos en H2 y comprueba la descarga PDF y el
rechazo del rol alumno en esta primera etapa. Las pruebas de servicio y HTTP
pasaron y se inspecciono visualmente el ejemplo sintetico de cinco paginas.

Se intento consultar los alumnos reales 128 y 8 mediante una conexion JDBC
de solo lectura. La conexion fallo por UnknownHostException: el entorno no
pudo resolver el servidor configurado. No se generaron reportes reales para
esos alumnos ni se modificaron sus datos. Esta comprobacion queda pendiente
desde un entorno con acceso a la base de datos.
