# US-20: libretas persistidas y PDF de progreso

El flujo utiliza InformeProgreso y DetalleInformeProgreso, sin cambiar sus campos
ni la migracion V10. El PDF se renderiza al descargarlo; sus resultados proceden
de la ultima version PUBLICADA, no de las calificaciones vigentes.

## Flujo de uso

En Evaluaciones > Libretas, seleccionar curso/seccion y periodo:

1. Generar borradores guarda una libreta por alumno con matricula activa o finalizada.
   Incluye evaluaciones activas no anuladas dentro de las fechas inclusivas del periodo.
   Copia nombre de competencia, titulo y fecha de evaluacion, ponderacion, nivel y
   observacion. Guarda referencias a evaluacion, competencia y calificacion de origen.
   Sin nota, el borrador muestra "Sin registrar"; no crea una nota ficticia.
2. Revisar borrador permite consultar los resultados por alumno. Volver a generar
   reemplaza los detalles del mismo borrador. Si la ultima version estaba publicada,
   crea una nueva version BORRADOR y conserva la anterior.
3. Publicar libretas exige niveles completos. Si las fuentes cambiaron desde la
   generacion, devuelve INFORME_DESACTUALIZADO y exige generar/revisar nuevamente.
   Publica el lote en una transaccion, registra fecha de publicacion y utiliza las
   validaciones existentes de EvaluacionService para publicar las evaluaciones.
   Si nunca se genero una libreta, la prepara en esa misma transaccion. Repetir la
   publicacion sin nuevos borradores devuelve cero y no crea versiones duplicadas.
4. Progreso y Descargar PDF leen la ultima version publicada. Una correccion de
   notas solo aparece despues de generar y publicar una nueva version.

La asignacion se bloquea durante generacion/publicacion para serializar numeros
de version concurrentes. El bloqueo optimista heredado sigue vigente.

## API

Todos los POST reciben idAsignacionCurso e idPeriodoAcademico y requieren
CALIFICACIONES_ESCRIBIR y rol DOCENTE titular o ADMINISTRADOR:

- POST /api/calificaciones/informes/generar: devuelve informesProcesados e informes
  con las opciones de alumnos para revisar.
- POST /api/calificaciones/informes/publicar: devuelve informesProcesados.

Consultas con CALIFICACIONES_LEER:

- GET /api/calificaciones/informes/borrador: ultima version, solo docente titular
  o administrador. Recibe idAlumno, idAsignacionCurso e idPeriodoAcademico.
- GET /api/calificaciones/informes-disponibles: opciones publicadas filtradas por
  identidad y relacion, una por alumno/curso/periodo.
- GET /api/calificaciones/avance: resultados guardados de la ultima publicada.
- GET /api/calificaciones/reporte-avance: mismos resultados como application/pdf,
  con Content-Disposition y Cache-Control: no-store.

Los ultimos dos reciben los mismos tres IDs que la consulta del borrador. Se
comprueban titularidad del docente, identidad del alumno o vinculo activo del
apoderado, matricula y correspondencia entre el año del curso y el periodo.
Alumno y apoderado no pueden consultar borradores ni generar/publicar libretas.

POST /api/evaluaciones/publicar-periodo sigue siendo publicacion de evaluaciones.
Publicar una evaluacion por separado NO publica la libreta. Las evaluaciones
anteriores pueden incorporarse generando/publicando libretas desde la pantalla.
No se hace una migracion automatica de publicaciones anteriores.

## Alcance de la conservacion historica

Se conservan los resultados y textos presentes en DetalleInformeProgreso. El PDF
incluye numero de version y estado. Cambiar o desactivar una nota de origen no
altera los detalles publicados. Las versiones anteriores permanecen en BD; la
pantalla de familias muestra la ultima publicada, incluso si existe un borrador
mas reciente. No se incorpora un selector de versiones historicas.

Las entidades actuales no copian nombres del alumno/docente/curso, codigo,
seccion ni etiquetas/fechas del periodo: la cabecera consulta esas relaciones.
El instante de renderizado tambien cambia en cada descarga. Por tanto se conserva
el resultado academico, no una copia binaria identica del PDF ni toda su cabecera.
Las referencias de origen quedan guardadas; no se agrega un visor de evidencias.

## Verificacion

Desde backend:

`./mvnw.cmd -pl controller -am test "-Dtest=ReporteAvanceTest,ReporteAvanceHttpTest,InformeProgresoPersistenciaTest,CalificacionServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

Pruebas HTTP/H2: permisos, vinculos revocados, matricula/año, fechas inclusivas,
niveles incompletos, regeneracion, conservacion del resultado, version nueva,
repeticion de publicacion y descarga PDF. ReporteAvanceTest renderiza un PDF real
con varias paginas y observaciones largas. InformeProgresoPersistenciaTest
comprueba el esquema V10 y sus restricciones. No sustituye una prueba en MySQL.

Desde frontend: `npm test -- src/modules/evaluations/tests` y `npx tsc --noEmit`.
