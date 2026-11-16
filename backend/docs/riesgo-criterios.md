# Evaluacion de riesgo por periodo

## Contrato

`POST /api/evaluaciones-riesgo` requiere `RIESGOS_ESCRIBIR` y recibe:

```json
{"idAlumno": 12, "idPeriodo": 3}
```

La respuesta contiene `idAlumno`, `idPeriodo`, `resultado` y `reglas`.
Cada regla incluye indicador, valor, operador, umbral, cantidad y minimo de datos,
motivo, evidencias de origen e identificador de alerta si corresponde.
Los resultados son `EN_RIESGO`, `SIN_RIESGO` y `DATOS_INSUFICIENTES`.
Si alguna regla detecta riesgo, el resultado general es EN_RIESGO; si ninguna lo
detecta pero alguna no puede evaluarse (o no hay reglas activas), es DATOS_INSUFICIENTES.

Este contrato reemplaza el anterior que aceptaba valores de indicadores enviados
por el cliente y devolvia un arreglo de alertas. No se aceptan esos valores como
fuente del calculo. Los consumidores deben enviar el periodo y leer `reglas`.
No se encontro una llamada a este endpoint en el frontend del repositorio.

`GET /api/alertas-riesgo/{id}` devuelve los valores de deteccion, el umbral y
operador guardados, periodo y `determinable`.
`GET /api/alertas-riesgo/{id}/historial` devuelve las evaluaciones sucesivas con
evidencias, incluidos cambios de datos y resolucion. Los cambios manuales de estado
siguen registrados en la auditoria existente.
`GET /api/alertas-riesgo/resumen?idPeriodo=3` agrupa alertas abiertas determinables
por grado, seccion, causa (indicador) y nivel. Devuelve tanto cantidad de alertas
como estudiantes distintos por grupo; los estudiantes pueden aparecer en varias causas.
Todas las consultas requieren RIESGOS_LEER y respetan las asignaciones del docente.

## Definiciones del calculo

Los umbrales y operadores siguen siendo configurados por el administrador.
Cada criterio admite `minimoDatos` >= 1 (por defecto 1); omitirlo al editar conserva
el valor anterior. El valor por defecto exige evidencia, no define una politica
institucional de muestra suficiente: el administrador debe configurar esa cantidad.

Se utiliza la matricula activa del alumno en el anio del periodo. Los registros
deben pertenecer a su seccion y a las fechas inclusivas del periodo.

| Indicador | Definicion | Cantidad de datos |
| --- | --- | --- |
| PROMEDIO_ACADEMICO | Escala existente C=1, B=2, A=3, AD=4. Consolidado ponderado por curso y competencia, redondeado a 2 decimales como el consolidado existente; media de esos consolidados a 4 decimales. | Calificaciones publicadas/cerradas activas |
| COMPETENCIAS_EN_INICIO | Cantidad de pares curso/competencia cuyo consolidado corresponde a C con `NivelLogro.desdePromedio`. | Calificaciones publicadas/cerradas activas |
| INASISTENCIAS | Registros INASISTENCIA, excluyendo las justificadas. | Registros definidos en sesiones TOMADA |
| TARDANZAS | Registros TARDANZA, excluyendo las justificadas. | Registros definidos en sesiones TOMADA |
| TAREAS_NO_ENTREGADAS | Tareas PUBLICADA/CERRADA vencidas en el periodo, sin entrega activa ENTREGADO, ENTREGADO_TARDE o CALIFICADO. | Tareas vencidas aplicables |

No se usan notas en borrador/anuladas, sesiones sin finalizar, SIN_MARCACION,
tareas en borrador/archivadas o aun no vencidas. Cero incidentes con registros
disponibles es distinto de no tener registros.

Estas definiciones explicitan como se agregan los datos existentes; deben revisarse
si el colegio usa otra politica (por ejemplo, incluir faltas justificadas o ponderar
los cursos de forma diferente).

## Actualizacion e historial

El recalculo es explicito mediante POST, tambien despues de corregir notas o asistencia.
No se agrega un proceso programado ni se recalcula al guardar cada nota.
Una alerta abierta se reutiliza para el mismo alumno, criterio y periodo.
Un bloqueo transaccional del alumno serializa sus recalculos concurrentes.
Si deja de cumplir se resuelve sin borrar la deteccion; el valor de cierre queda
en el historial. Si vuelve a cumplir despues del cierre se crea otra alerta.
Si faltan datos se conserva la alerta previa y su DTO devuelve `determinable=false`, sin cerrarla
ni contarla en el resumen o en los indicadores de riesgo confirmado del dashboard.
El cliente debe mostrar esa condicion, no interpretar ACTIVA por si sola como una
clasificacion actual confirmada.

## Persistencia sin modificar entidades

Las entidades y el esquema permanecen como en el proyecto original. No se agrega
HistorialRiesgo ni una migracion V10. AuditableEntity conserva fechas, estado activo
y version; RegistroAuditoria conserva los detalles historicos de las evaluaciones.

- `AlertaRiesgo` guarda exclusivamente sus campos originales: alumno, criterio,
  valor detectado, nivel, estado, mensaje, observacion y fechas.
- `EVALUAR_CRITERIO_RIESGO` en `RegistroAuditoria` guarda una instantanea JSON
  versionada en `nuevoValor`: periodo, seccion, grado, valores, regla y evidencias.
  Se asocia a AlertaRiesgo cuando hay alerta, o a Alumno cuando no se crea ninguna.
- `CONFIGURAR_MINIMO_RIESGO` registra el minimo de datos del criterio. La lectura
  recupera su ultima configuracion; si nunca se configuro, exige al menos un dato.
  `minimoDatos` es un atributo del DTO, no de la entidad CriterioRiesgo.
- `periodo`, `umbral`, `operador` y `determinable` de la respuesta de alerta se
  obtienen de la auditoria. No son columnas nuevas ni se sobrecargan observaciones
  o descripciones con datos internos.
- El detalle conserva la ultima deteccion positiva; el historial incluye tambien
  cierres y datos insuficientes. El resumen usa la ultima evaluacion de cada alerta
  y respeta los filtros de asignacion docente existentes.
- La evidencia extensa se fragmenta en registros `RIESGO_EVIDENCIA_PARTE` para
  respetar el limite de las columnas TEXT existentes, sin truncar informacion.
  Los fragmentos y su registro principal se escriben en la misma transaccion.

Estos registros de auditoria forman parte del historial funcional y deben
conservarse. Eliminarlos impediria recuperar el periodo, las evidencias y la
configuracion minima. Las consultas proyectan esa informacion desde la auditoria;
para volumenes grandes puede ser necesario optimizar su lectura, sin cambiar entidades.

Las alertas antiguas que no tienen estas instantaneas conservan su mensaje y
pueden consultarse y cerrarse manualmente. Su detalle no inventa umbral o periodo;
no se incluyen en el nuevo resumen por periodo. Los contadores generales existentes
mantienen su tratamiento anterior de esas alertas. Un recalculo crea una alerta
con evidencia del periodo real, sin atribuirle un periodo supuesto a la antigua.

## Verificacion

`RiesgoServiceTest` cubre los cinco operadores antes/en/despues del umbral.
`RiesgoCriteriosHttpTest` cubre el flujo HTTP, periodos, actualizacion, cierre,
datos insuficientes, resumen, alcance docente, fuentes de notas/asistencia/tareas,
persistencia en auditoria y reconstruccion de evidencias grandes. Las pruebas
usan H2 con las entidades originales; esta implementacion no requiere cambios DDL.

Verificacion de esta version: 37 pruebas aprobadas, sin fallos ni errores, en las
clases de riesgo, dashboard, permisos, repositorios con alcance y flujos de pagos.
Git confirma que no hay modificaciones ni archivos nuevos en entidades o migraciones.
