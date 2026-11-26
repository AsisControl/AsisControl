# OBS-05 · Carga masiva por módulo — análisis (ASISCTRL-228)

> **Borrador para aprobación del equipo.** Las columnas de las plantillas 230 a 234 se cierran
> cuando el equipo apruebe este documento. Lo que ya está implementado (229) puede ajustarse.

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
| No duplicar (CA-4) | Cada plantilla define una **clave natural**. Si la clave ya existe en la base o se repite en el archivo, la fila se **omite** y no cuenta como error. Al subir dos veces el mismo archivo, la segunda vez todas las filas quedan omitidas. |
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

### 3.3 Cursos y plan de estudios (230) — por cerrar

Una fila por curso y grado del plan:

| Columna | | Valores |
|---|---|---|
| anioAcademico | O | año del plan (BORRADOR o editable) |
| grado | O | 1–5 |
| codigoCurso | O | se crea el curso si no existe en el catálogo |
| nombreCurso | O | hasta 120 |
| horasSemanales | O | 1–40; se valida contra el tope de horas del plan |
| codigosCompetencias | o | separados por `;`, deben existir en el catálogo |
| descripcion | o | hasta 500 |

Clave natural: `anioAcademico + grado + codigoCurso`. Se guarda con la misma operación del plan
(`GuardarPlan`), agrupando las filas por año y grado.

### 3.4 Asignaciones docentes (231) — por cerrar

| Columna | | Valores |
|---|---|---|
| anioAcademico, grado, seccion | O | identifican la sección |
| codigoCurso | O | el curso debe estar en el plan del grado |
| codigoDocente | O | docente activo |

Clave natural: `sección + curso` (solo una asignación activa por curso y sección).

### 3.5 Horarios (231) — por cerrar

| Columna | | Valores |
|---|---|---|
| anioAcademico, grado, seccion, codigoCurso | O | identifican la asignación |
| codigoAula | O | aula habilitada con capacidad suficiente |
| diaSemana | O | lista `DiaSemana` |
| horaInicio, horaFin | O | `HH:mm` |

Clave natural: `asignación + día + horaInicio`. Los cruces de aula, docente y sección y las horas
semanales del plan se validan en `HorarioService`.

### 3.6 Tarifas de pensión (233) — por cerrar

| Columna | | Valores |
|---|---|---|
| codigoConcepto | O | concepto de cobro existente |
| anioAcademico | O | |
| grado | O | 1–5 |
| montoMensual | O | > 0, con 2 decimales |

Clave natural: `concepto + año + grado`.

### 3.7 Matrículas con sección (232) — por cerrar

| Columna | | Valores |
|---|---|---|
| codigoAlumno | O | alumno habilitado |
| anioAcademico, grado, seccion | O | sección activa con cupo |
| observaciones | o | hasta 1000 |

Clave natural: `alumno + año`. Cada fila crea la solicitud y la matrícula con el mismo servicio
que el flujo manual, que también genera las cuotas.

### 3.8 Notas en la planilla del docente (234) — por cerrar

Se descarga por evaluación y ya trae a los alumnos matriculados:

| Columna | | Valores |
|---|---|---|
| codigoAlumno | O | prellenado |
| alumno | — | solo para leer |
| codigoCompetencia | O | competencias del curso |
| nivelLogro | O | lista `NivelLogro` |
| observacion | o | hasta 1000 |

Clave natural: `evaluación + alumno + competencia`. Si la nota ya existe, la fila se **omite**:
para corregirla se usa el flujo de corrección con motivo. Permiso: el docente de la asignación.

## 4. Pruebas (ASISCTRL-235)

Cada rama incluye, por plantilla, pruebas HTTP de integración con tres archivos:

1. **Válido**: la revisión no guarda nada; la importación crea todas las filas.
2. **Con errores**: devuelve fila, campo y mensaje, y no guarda las filas inválidas.
3. **Repetido**: al cargar dos veces el mismo archivo, la segunda vez todas las filas quedan
   omitidas y no hay registros nuevos.

Además se prueba que un usuario sin permiso reciba 403.

## 5. Decisiones abiertas para el equipo

- ¿La fila repetida se **omite** (propuesta) o se **actualiza** con los datos del archivo? La
  propuesta es omitir para no editar sin motivo.
- Notas: ¿una plantilla por evaluación (propuesta) o una por asignación con varias evaluaciones?
- Matrículas: ¿la carga masiva crea la matrícula directa o solo la solicitud para que secretaría
  la apruebe?
