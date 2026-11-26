# OBS-05 · Carga masiva por módulo — análisis (ASISCTRL-228)

> **Para revisión del equipo.** Las ocho plantillas (229 a 234) ya están implementadas con estas
> columnas; cualquier ajuste que el equipo pida se aplica sobre ellas.

## 1. Reglas comunes para todas las plantillas

Se reutiliza lo que ya funciona en estudiantes, docentes y competencias (`LectorExcel`,
`PlantillaExcel`):

| Regla | Detalle |
|---|---|
| Formato | Solo `.xlsx` de hasta 5 MB. Primera hoja, cabecera en la fila 1 y filas vacías omitidas. |
| Plantilla | Se descarga desde el botón **Carga masiva** del módulo. Trae una fila de ejemplo y listas desplegables para los valores cerrados (enumerados, SI/NO). |
| Dos pasos | **Revisar archivo** procesa todo dentro de una transacción que siempre se deshace, así que no guarda nada. **Importar filas válidas** guarda cada fila en su propia transacción. |
| Mismas reglas | Cada fila llama al mismo servicio que el registro manual (cupos, cruces, horas del plan, año activo, motivo). Los endpoints usan el mismo permiso que el alta manual. |
| Errores (CA-2) | Cada error indica **fila, campo y mensaje**. Las filas con error no se guardan y no impiden guardar las demás. |
| No duplicar (CA-4) | Cada plantilla define una **clave natural**. Si la clave ya existe en la base o se repite en el archivo, la fila se **omite** y no cuenta como error. Al subir dos veces el mismo archivo, la segunda vez todas las filas quedan omitidas. En notas en borrador, un nivel distinto se actualiza, como en la planilla. |
| Referencias | Se usan códigos legibles (código de aula, código de curso, código de docente o alumno, año + grado + sección) y nunca ids internos. |
| Auditoría | Cada alta deja su propio registro, igual que en el registro manual. |
| Respuesta | `{ total, validas, creadas, omitidas[], errores[] }` con `{ row, field, message }`, el mismo formato que competencias. |

Endpoints por módulo: `POST /api/<recurso>/importaciones?confirmar=false|true` (multipart
`archivo`) y `GET /api/<recurso>/importaciones/plantilla`.

## 2. Orden de carga

Cada paso necesita los datos del anterior:

| # | Módulo | Depende de | Subtarea | Rama |
|---|---|---|---|---|
| 0 | Estudiantes, apoderados, parentescos, docentes, competencias | — | ya existe | — |
| 1 | Aulas | — | 229 | `feature/ASISCTRL-228-229-carga-masiva-base` |
| 2 | Secciones | año académico con grado ofertado, docente (tutor) | 229 | ídem |
| 3 | Cursos y plan de estudios | grados, competencias | 230 | `feature/ASISCTRL-230-231-carga-academica` |
| 4 | Asignaciones docentes | cursos en el plan, secciones, docentes | 231 | ídem |
| 5 | Horarios | asignaciones, aulas | 231 | ídem |
| 6 | Tarifas de pensión | conceptos de cobro, año, grado | 233 | `feature/ASISCTRL-232-233-carga-matricula-tarifas` |
| 7 | Matrículas con sección | alumnos, secciones con cupo, tarifa | 232 | ídem |
| 8 | Notas en la planilla | evaluaciones, matrículas, competencias del curso | 234 | `feature/ASISCTRL-234-carga-notas` |

Las tarifas van antes que las matrículas porque matricular genera las cuotas del plan con la
tarifa del grado.

## 3. Columnas por plantilla

Leyenda: **O** = obligatoria, o = opcional.

### 3.1 Aulas (229) — `/api/aulas/importaciones` · permiso `ACADEMICO_ESCRIBIR`

| Columna | | Valores |
|---|---|---|
| codigo | O | hasta 30 caracteres, sin distinguir mayúsculas |
| nombre | O | hasta 100 |
| tipoAula | O | lista: valores de `TipoAula` |
| capacidad | O | entero de 1 a 300 |
| ubicacion | o | hasta 180 |

Clave natural: `codigo`.

### 3.2 Secciones (229) — `/api/secciones/importaciones` · rol ADMINISTRADOR + `ACADEMICO_ESCRIBIR`

| Columna | | Valores |
|---|---|---|
| anioAcademico | O | 2000–2100; el grado debe estar ofertado ese año |
| grado | O | 1–5 |
| nombre | O | hasta 20 (A, B, …) |
| capacidadMaxima | O | 1–100 |
| codigoDocenteTutor | o | código del docente activo |
| motivo | o | obligatorio si el año está ACTIVO (misma regla que el alta manual) |

Clave natural: `anioAcademico + grado + nombre`.

### 3.3 Cursos y plan de estudios (230) — `/api/planes-estudios/importaciones` · rol ADMINISTRADOR + `ACADEMICO_ESCRIBIR`

Una fila por curso, año y grado del plan:

| Columna | | Valores |
|---|---|---|
| anioAcademico | O | año del plan (no cerrado) |
| grado | O | 1–5, habilitado en el año |
| codigoCurso | O | si no existe en el catálogo, se registra con los datos de la fila |
| nombreCurso | O | hasta 120; es el nombre del curso en el plan |
| horasSemanales | O | 1–40 |
| codigosCompetencias | o | separados por `;`; vacío = competencias base del curso |
| descripcion | o | solo se usa si el curso es nuevo |
| motivo | o | obligatorio si el plan ya estaba definido antes del archivo (misma regla que la pantalla) |

Clave natural: `anioAcademico + grado + codigoCurso`. Cada fila se guarda con
`PlanEstudiosService.guardar`. Las filas siguientes del mismo grado en el mismo archivo usan como
motivo la propia carga masiva. Si el curso nuevo se crea pero el plan rechaza la fila, también se
deshace el curso.

### 3.4 Asignaciones docentes (231) — `/api/asignaciones-cursos/importaciones` · `ACADEMICO_ESCRIBIR`

| Columna | | Valores |
|---|---|---|
| anioAcademico, grado, seccion | O | identifican la sección activa |
| codigoCurso | O | el curso debe estar en el plan del grado |
| codigoDocente | O | docente activo |

Clave natural: `sección + curso` (solo una asignación activa por curso y sección).

### 3.5 Horarios (231) — `/api/horarios/importaciones` · `ACADEMICO_ESCRIBIR`

| Columna | | Valores |
|---|---|---|
| anioAcademico, grado, seccion, codigoCurso | O | identifican la asignación activa |
| codigoAula | O | aula habilitada con capacidad para la sección |
| diaSemana | O | lista `DiaSemana` |
| horaInicio, horaFin | O | `HH:mm` entre 07:00 y 20:00, en pasos de 30 minutos |

Clave natural: `asignación + día + horaInicio`. `HorarioService` valida los cruces de aula,
docente y sección.

> **Observación:** hoy ni el registro manual ni la carga masiva comparan las horas de los bloques
> con las `horasSemanales` del curso en el plan. Si el equipo quiere esa regla, debe agregarse en
> `HorarioService` para que se aplique a ambos.

### 3.6 Tarifas de pensión (233) — `/api/configuracion-pagos/tarifas/importaciones` · `PAGOS_ESCRIBIR` (administración, dirección, secretaría o tesorería)

| Columna | | Valores |
|---|---|---|
| codigoConcepto | O | concepto de cobro activo; debe ser el de pensión |
| anioAcademico | O | año no cerrado |
| grado | O | 1–5 |
| montoMensual | O | > 0, con punto o coma decimal |

Clave natural: `concepto + año + grado`. Cada fila pasa por
`ConfiguracionPagosService.guardarTarifa`. Una tarifa ya registrada se omite: para cambiar su
monto se edita en la pantalla, con su versión.

### 3.7 Matrículas con sección (232) — `/api/matriculas/importaciones` · rol ADMINISTRADOR + `MATRICULA_ESCRIBIR`

| Columna | | Valores |
|---|---|---|
| codigoAlumno | O | estudiante habilitado |
| anioAcademico, grado, seccion | O | sección activa del año **activo** |
| observaciones | o | hasta 1000 |

Clave natural: `alumno + año`; se omite si el alumno ya tiene una matrícula vigente ese año.
**Decisión tomada:** cada fila crea la matrícula completa con el mismo flujo que la secretaría
(solicitud → ENVIADA → EN_REVISION → sección asignada → matrícula finalizada con la firma del
plan de cuotas). Se aplican las mismas reglas: año activo, grado ofertado, cupo de la sección,
tarifa del grado y generación de las cuotas. Si un paso falla, la fila completa se deshace. Por
eso las tarifas se cargan antes que las matrículas.

### 3.8 Notas en la planilla del docente (234) — `/api/evaluaciones/{id}/calificaciones/importaciones` · `CALIFICACIONES_ESCRIBIR` + docente de la asignación (o administrador)

Una plantilla por evaluación. Se descarga desde el botón **Carga masiva** de la planilla y ya trae
una fila por alumno matriculado y competencia de la evaluación, con los niveles guardados:

| Columna | | Valores |
|---|---|---|
| codigoAlumno | O | prellenado |
| alumno | — | prellenado, solo para leer |
| codigoCompetencia | O | prellenado; debe ser de la evaluación |
| competencia | — | prellenado, solo para leer |
| nivelLogro | o | lista `NivelLogro` (AD, A, B, C); vacío = no se registra |
| observacion | o | hasta 1000 |

Clave natural: `evaluación + alumno + competencia`. Cada fila pasa por
`CalificacionService.registrar`, con las mismas reglas que la planilla: evaluación en **borrador**,
alumno con matrícula activa en la sección y competencia de la evaluación. Como en la planilla, en
borrador un nivel puede cambiarse: si el archivo trae otro nivel, se actualiza (no se duplica); si
trae el mismo, la fila se omite. Una evaluación publicada no admite carga masiva: se corrige con
motivo en la planilla.

## 4. Pruebas (ASISCTRL-235)

Cada rama incluye, por plantilla, pruebas HTTP de integración con tres archivos:

1. **Válido**: la revisión no guarda nada; la importación crea todas las filas.
2. **Con errores**: devuelve fila, campo y mensaje, y no guarda las filas inválidas.
3. **Repetido**: al cargar dos veces el mismo archivo, la segunda vez todas las filas quedan
   omitidas y no hay registros nuevos.

Además se prueba que un usuario sin permiso reciba 403.

## 5. Decisiones tomadas (para validar con el equipo)

- Fila ya registrada: se **omite** en todos los módulos, salvo en notas en borrador, donde se
  actualiza el nivel igual que en la planilla manual.
- ~~Notas: ¿una plantilla por evaluación o por asignación?~~ Una por evaluación, prellenada.
- ~~Matrículas: ¿matrícula directa o solo la solicitud?~~ Se implementó la matrícula completa con
  el flujo de la secretaría (ver 3.7); si el equipo prefiere solo la solicitud, basta con cortar
  el flujo después de crearla.
