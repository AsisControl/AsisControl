# US-20: publicacion de notas y PDF de progreso

Genera un PDF al solicitarlo, sin almacenar informes ni crear entidades. Los datos
corresponden al momento de la consulta. No conserva versiones anteriores.

## Descarga

`GET /api/calificaciones/reporte-avance?idAlumno=1&idAsignacionCurso=2&idPeriodoAcademico=3`

Los identificadores son ejemplos: usar los existentes en la base de datos.
Enviar la autenticacion habitual del API. La respuesta es `application/pdf`,
con nombre de descarga y `Cache-Control: no-store`.

Admite docentes titulares del curso, administradores, el propio alumno y sus
apoderados con vinculo activo y `CALIFICACIONES_LEER`. La relacion se comprueba
en cada consulta y descarga. Solo se muestran notas publicadas o cerradas.

## Publicacion y consulta

El docente usa Evaluaciones > Publicar notas, selecciona curso y periodo y
confirma. `POST /api/evaluaciones/publicar-periodo` recibe `idAsignacionCurso`
e `idPeriodoAcademico`; exige `CALIFICACIONES_ESCRIBIR` y la titularidad del curso
(o administrador). Publica los borradores de las fechas del periodo en una sola
transaccion, reutilizando las validaciones y auditoria de EvaluacionService.
Una planilla incompleta impide publicar el lote: todos los alumnos matriculados deben tener nivel de logro en todas las competencias de la evaluacion. Repetir la solicitud devuelve
cero publicaciones si ya no quedan borradores. No crea permisos globales ni
entidades de informe: PUBLICADA habilita la visibilidad de las notas.

En Progreso, alumnos y apoderados eligen entre las opciones que el servidor
devuelve en `GET /api/calificaciones/informes-disponibles`, consultan el detalle
mediante `GET /api/calificaciones/avance` y descargan el PDF. Ambos endpoints de
detalle reciben los mismos tres IDs que la descarga. Los IDs enviados por el
cliente no sustituyen la comprobacion de identidad y relacion.

Las evaluaciones publicadas previamente tambien quedan disponibles; no existe
una segunda marca de publicacion del informe. Corregir una nota actualiza la
siguiente consulta o descarga. Anular la evaluacion la excluye del informe.

El alumno debe tener matricula activa o finalizada en la seccion. El periodo
academico debe estar activo (no eliminado), pertenecer al año de la seccion y
se filtra por fechas inclusivas de evaluacion. No confundir el ID del periodo
academico con el enum PeriodoEvaluacion (BIMESTRAL, MENSUAL, etc.).

Se incluyen calificaciones activas de evaluaciones activas publicadas o cerradas.
Sin evaluaciones publicadas en el periodo se devuelve un error de negocio. Las competencias publicadas sin calificacion se incluyen sin crear notas ni asignarles cero.

## Contenido

Alumno y codigo, curso, grado, seccion, año, docente, periodo y fechas.
Detalle ordenado por competencia, fecha de evaluacion e ID de evaluacion:
competencia, evaluacion, fecha, nivel registrado, ponderacion y observacion.
Las observaciones vacias se muestran como "Sin observaciones". La ponderacion
es la registrada en cada evaluacion; no se renormaliza al conjunto del informe.
La fecha de generacion usa America/Lima.

El consolidado por competencia y las referencias de evidencia quedan pendientes
de definir sus reglas. No se convierten niveles a numeros ni se infieren evidencias.
La pantalla Progreso incluye la consulta y el boton de descarga.

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

La prueba HTTP usa datos persistidos en H2 para comprobar publicacion, consulta,
descarga y aislamiento entre alumnos, apoderados y docentes. Se inspecciono
visualmente el ejemplo sintetico de cinco paginas de la primera etapa.

Se intento consultar los alumnos reales 128 y 8 mediante una conexion JDBC
de solo lectura. La conexion fallo por UnknownHostException: el entorno no
pudo resolver el servidor configurado. No se generaron reportes reales para
esos alumnos ni se modificaron sus datos. Esta comprobacion queda pendiente
desde un entorno con acceso a la base de datos.
