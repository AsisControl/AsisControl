# US-20: contrato de sustento autorizado por fila

## Identificadores de la libreta

GET /api/calificaciones/avance y GET /api/calificaciones/informes/borrador
conservan los textos de cada fila y agregan:

```json
{
  "idInforme": 41,
  "idDetalleInforme": 203,
  "idEvaluacion": 18,
  "idCompetencia": 7,
  "idCalificacion": 92,
  "competencia": "Resuelve problemas",
  "evaluacion": "Practica",
  "fecha": "15/03/2026",
  "resultado": "A",
  "ponderacion": "30 %",
  "observacion": "Sin observaciones"
}
```

IDs ilustrativos: el cliente debe usar los devueltos por el servidor. idCalificacion
puede ser null en un borrador incompleto. La fila conserva el ID de su version;
no se resuelve silenciosamente contra la ultima libreta publicada.

## Consultar el sustento

`GET /api/calificaciones/informes/{idInforme}/filas/{idDetalle}/evidencias`

Devuelve idInforme, idDetalleInforme, numeroVersion, idAlumno, idAsignacionCurso,
idPeriodoAcademico, idEvaluacion, idCompetencia, idCalificacion y evidencias:

```json
{
  "idInforme": 41,
  "idDetalleInforme": 203,
  "numeroVersion": 1,
  "idAlumno": 8,
  "idAsignacionCurso": 3,
  "idPeriodoAcademico": 4,
  "idEvaluacion": 18,
  "idCompetencia": 7,
  "idCalificacion": 92,
  "evidencias": [{
    "idEvidencia": 51,
    "descripcion": "Resolucion del problema",
    "archivo": {
      "idArchivo": 67,
      "nombreOriginal": "trabajo.pdf",
      "tipoMime": "application/pdf",
      "tamanioBytes": 12345,
      "urlDescarga": "/api/calificaciones/informes/41/filas/203/evidencias/51/archivo"
    }
  }]
}
```

- Sin evidencias compatibles: 200 con evidencias: []. No se inventa sustento.
- Evidencia textual sin archivo, o adjunto inactivo: archivo: null.
- `GET .../evidencias/{idEvidencia}` devuelve una evidencia concreta de esa fila.
- `GET .../evidencias/{idEvidencia}/archivo` descarga el archivo con autenticacion.
  El servidor obtiene idArchivo de la evidencia; no admite un archivo arbitrario.
- Todas las respuestas usan Cache-Control: no-store. La descarga es attachment
  con X-Content-Type-Options: nosniff. No se exponen rutas fisicas del almacenamiento.

El frontend debe consultar el endpoint al abrir "Ver sustento" de la fila,
mostrar descripcion y adjunto, y descargar urlDescarga mediante su cliente
API autenticado. Este cambio entrega el contrato/backend; no agrega ese boton.
Los endpoints generales de evidencias y archivos conservan sus permisos previos.

## Reglas de autorizacion y correspondencia

Se exige CALIFICACIONES_LEER y rol ADMINISTRADOR, DOCENTE, ALUMNO o APODERADO.
Se verifica en cada consulta y descarga:

1. Detalle e informe activos y pertenencia exacta del detalle al idInforme.
2. Docente titular o administrador; para familias, alumno propio o vinculo activo
   del apoderado. Un informe BORRADOR es exclusivo de docente titular/admin.
3. Asignacion/docente/alumno/periodo/año activos, año del periodo compatible y
   matricula activa o finalizada en la seccion.
4. Evidencia activa, del MISMO alumno, asignacion, competencia y periodo, y con
   vinculo EXPLICITO a idEvaluacion en EvidenciaPedagogicaEvaluacion.
5. En descarga, la evidencia debe cumplir nuevamente todas las condiciones y
   tener un archivo activo. Revocar el vinculo familiar revoca tambien la descarga.

403: usuario sin permiso, ajeno, borrador no autorizado o contexto deshabilitado.
404 FILA_INFORME_NO_ENCONTRADA: detalle inexistente/inactivo o de otro informe.
404 SUSTENTO_NO_ENCONTRADO: evidencia inexistente/inactiva o de otro contexto.
404 SUSTENTO_SIN_ARCHIVO: evidencia autorizada sin adjunto activo.
Si falta el fichero fisico se conserva ARCHIVO_FISICO_NO_ENCONTRADO.

## Limite historico

Los IDs de evaluacion/competencia/calificacion provienen del detalle persistido.
Las evidencias se consultan desde sus vinculos actuales: las entidades existentes
no guardan una copia de la lista de evidencias al publicar. Por ello, una evidencia
vinculada despues puede aparecer tambien en el sustento de una version anterior.
No se afirma que esta lista sea una fotografia del momento de publicacion.
La nota y observacion publicadas siguen siendo las copias guardadas en el detalle.
Una evaluacion posteriormente anulada no borra la trazabilidad de esa fila
historica, mientras el informe/contexto y la evidencia sigan autorizados y activos.

## Pruebas

SustentoInformeHttpTest cubre IDs reales, los cuatro roles, descarga de bytes,
cruces de alumno/curso/periodo/competencia/evaluacion, vinculo explicito obligatorio,
evidencia inactiva, borradores, permisos ausentes, revocacion familiar y adjunto
inactivo. ReporteAvanceHttpTest/ReporteAvanceTest cubren regresion de libreta/PDF;
EvidenciaPedagogicaHttpTest verifica que la gestion de evidencias conserva sus reglas.
