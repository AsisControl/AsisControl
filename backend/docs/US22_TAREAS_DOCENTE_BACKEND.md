# US-22: backend de tareas del docente y ASISCTRL-62

Completa lo que faltaba en el backend para el frontend de US-22 (ASISCTRL-85 a 88)
y la validación de documentos de ASISCTRL-62. Base de trabajo: `develop`, commit `4691bf2`.

## Editar una tarea publicada

`PUT /api/tareas/{id}` ahora acepta tareas en `BORRADOR` **o** `PUBLICADA`
(`CERRADA` y `ARCHIVADA` siguen respondiendo 422 `TAREA_TRANSICION_INVALIDA`).
El cuerpo agrega un campo opcional:

```json
{ "nombre": "...", "descripcion": "...", "fechaPublicacion": "2026-11-01T08:00:00",
  "fechaLimite": "2026-11-10T23:59:00", "puntajeMaximo": 20, "maxArchivosEntrega": 2,
  "archivoIds": [], "version": 3, "motivo": "Se amplía por feriado" }
```

Reglas de una tarea `PUBLICADA` (todas responden 422 con `field`):

| Código | Cuándo |
| --- | --- |
| `TAREA_PUBLICACION_NO_EDITABLE` | `fechaPublicacion` distinta de la actual. Enviar la misma o `null` |
| `TAREA_FECHA_LIMITE_VENCIDA` | Se cambia `fechaLimite` a una fecha que no es futura |
| `TAREA_MOTIVO_REQUERIDO` | La tarea tiene entregas, cambia `fechaLimite` y `motivo` está vacío |
| `TAREA_PUNTAJE_MENOR_A_NOTAS` | `puntajeMaximo` menor que una nota ya registrada |

- Sin cambiar `fechaLimite` no hace falta motivo.
- Al cambiar el vencimiento, las entregas sin calificar se recalculan como
  `ENTREGADO` o `ENTREGADO_TARDE` según la nueva fecha (cambia su `version`).
- Auditoría `ACTUALIZAR_TAREA` con el motivo, el valor anterior y el nuevo
  (incluye `fechaLimite` y `entregasRecalculadas`).

## Eliminar una tarea

`DELETE /api/tareas/{id}` con cuerpo opcional `{ "motivo": "..." }` (máx. 500).

- Se permite en cualquier estado (baja lógica). Antes solo en borrador o archivada.
- Si la tarea tiene entregas, el motivo es obligatorio: 422 `TAREA_MOTIVO_REQUERIDO`.
- Las entregas se conservan; la tarea deja de mostrarse a docentes, alumnos y apoderados.
- Auditoría `ELIMINAR_TAREA` con el motivo y la cantidad de entregas.

## Asignaciones del docente autenticado

`GET /api/asignaciones-cursos/mias?anioAcademico=2026` devuelve `Page<AsignacionCursoResponse>`
con las asignaciones **activas** de la persona de la sesión. No usa el catálogo global:
un administrador sin perfil docente recibe `content: []`. Requiere `ACADEMICO_LEER`.

## Padrón de seguimiento

`GET /api/tareas/{id}/seguimiento` (administración, dirección o el docente de la tarea;
alumnos y apoderados reciben 403):

```json
{ "tareaId": 1, "totalAlumnos": 30, "entregados": 3, "calificados": 1, "pendientes": 27,
  "alumnos": [
    { "alumnoId": 8, "codigoAlumno": "EST-8", "nombreCompleto": "Lucía Pérez",
      "matriculaActiva": true, "entregaId": 10, "estado": "CALIFICADO",
      "fechaHoraEntrega": "2026-11-05T12:30:45", "nota": 18 },
    { "alumnoId": 9, "codigoAlumno": "EST-9", "nombreCompleto": "Luis Ramos",
      "matriculaActiva": true, "entregaId": null, "estado": "PENDIENTE",
      "fechaHoraEntrega": null, "nota": null } ] }
```

- Incluye a todos los alumnos con matrícula `ACTIVA` en la sección, ordenados por nombre.
- `PENDIENTE` con `entregaId: null` significa "Sin entrega".
- `entregados` cuenta `ENTREGADO`, `ENTREGADO_TARDE` y `CALIFICADO` (no `ANULADO`).
- Quien entregó y luego dejó la sección aparece con `matriculaActiva: false` y no suma al total.

## ASISCTRL-62: documento y teléfono

`PersonaRequest` (docente, apoderado, alta de accesos e importación de familias) aplica
las mismas reglas que `PersonFields` en el frontend:

| Campo | Regla | Mensaje |
| --- | --- | --- |
| DNI | Exactamente 8 dígitos | `El DNI debe tener exactamente 8 dígitos` |
| Carné / pasaporte | `[A-Za-z0-9-]{6,20}` | `El documento no tiene un formato valido` |
| Teléfono (opcional) | `[0-9+() -]{7,20}` | `El telefono no tiene un formato valido` |

La ficha de estudiante mantiene sus reglas propias (`AlumnoPersonaValidator`):
carné de 9 dígitos, pasaporte de 8 a 12 caracteres y celular `9XXXXXXXX`.

## Pruebas

- `TareaSeguimientoHttpTest` (7): edición publicada, motivo y recálculo, puntaje,
  eliminación con motivo, padrón, permisos del padrón y asignaciones propias.
- `DocumentoPersonaHttpTest` (3): DNI, carné/pasaporte y teléfono.
