# Iteración 2: filtros de horarios e historial de matrícula

Implementación backend de ASISCTRL-97 (US-24) y ASISCTRL-105 (US-49).
Base de trabajo: `develop`, commit `25e405a`.

## ASISCTRL-97: consultar horarios con filtros

`GET /api/horarios` conserva el contrato paginado `Page<HorarioResponse>`.
Todos los filtros son opcionales y se combinan con **AND**; se aplican en la base
de datos antes de paginar.

| Parámetro | Significado | Validación |
| --- | --- | --- |
| `anioAcademico` | Año numérico de la sección, por ejemplo `2026`; no es el ID del año | 2000 a 2100 |
| `seccionId` | ID de la sección | Entero positivo |
| `docenteId` | ID del perfil Docente; no es el ID de Persona o AccesoUsuario | Entero positivo |
| `aulaId` | ID del aula | Entero positivo |
| `cursoId` | ID del curso | Entero positivo |

Ejemplos (sustituir los IDs por registros del entorno):

```http
GET /api/horarios?anioAcademico=2026&seccionId=3
GET /api/horarios?anioAcademico=2026&docenteId=7
GET /api/horarios?anioAcademico=2026&aulaId=2
GET /api/horarios?anioAcademico=2026&cursoId=5
GET /api/horarios?anioAcademico=2026&seccionId=3&aulaId=2&page=0&size=20
```

- Requiere un token de acceso válido y el permiso `ACADEMICO_LEER`.
- Devuelve únicamente horarios activos de asignaciones de curso activas.
- Sin filtros conserva el listado de horarios vigentes.
- Un filtro válido sin coincidencias devuelve HTTP 200, `content: []` y
  `totalElements: 0`; no devuelve 404.
- IDs no positivos, años fuera de rango o valores de tipo incorrecto devuelven 400.
- La respuesta mantiene los IDs y nombres de curso, sección, docente y aula,
  día, horas, estado y versión del bloque.
- El frontend debe recorrer las páginas si necesita cargar todos los bloques:
  no asumir que la primera página representa toda la matriz.
- Tras editar un bloque, volver a consultar refleja la versión vigente.
- No se agrega un filtro por período: Horario/AsignacionCurso no tienen esa
  relación en el modelo actual. Si el equipo lo exige, requiere acordar el modelo
  y el contrato con quienes implementan US-23/US-58.

Las cuatro perspectivas se construyen en el frontend a partir de estos mismos
datos. No se modificaron componentes frontend.

## ASISCTRL-105: historial para el apoderado asociado

```http
GET /api/solicitudes-matricula/{id}/historial
Authorization: Bearer <token>
```

`id` es el ID de la **solicitud**, no el ID de la matrícula ni del estudiante.

Acceso permitido:

- Administrador con `MATRICULA_LEER`: conserva el acceso existente.
- Apoderado con `MATRICULA_LEER`: solo cuando existe una asociación activa con
  el estudiante de la solicitud. La validación existente también exige que el
  apoderado, el alumno y sus personas estén activos, así como la solicitud.

Respuestas:

| Situación | HTTP |
| --- | --- |
| Acceso autorizado | 200, arreglo de eventos en orden cronológico ascendente |
| Historial autorizado sin eventos | 200, `[]` |
| Sin autenticación | 401 |
| Apoderado no asociado, vínculo inactivo, otro rol o falta de permiso | 403 |
| Solicitud inexistente o inactiva consultada por administrador autorizado | 404 |
| ID inexistente consultado por apoderado sin asociación comprobable | 403 |

Cada evento conserva el contrato existente:

```json
{
  "id": 101,
  "actor": "usuario-autorizado",
  "rol": "ADMINISTRADOR",
  "fecha": "2026-10-30T09:00:00Z",
  "accion": "GUARDAR_SOLICITUD",
  "motivo": null,
  "anterior": null,
  "nuevo": "estado=BORRADOR,seccion=null"
}
```

El historial se obtiene de la auditoría de `SolicitudMatricula`. No incluye los
eventos de otras solicitudes o entidades, ni agrega IP o datos técnicos del log.
El backend valida el vínculo desde la identidad autenticada; no acepta un
`apoderadoId` enviado por el cliente como prueba de autorización.

No se necesita crear tablas, ejecutar INSERT ni agregar migraciones para estos
cambios. La línea de tiempo visual corresponde a ASISCTRL-106.

## Pruebas y revisión

Las nuevas clases están en `controller/src/test/java/com/asiscontrol/controller`:

- `HorarioFiltrosHttpTest`: filtros individuales y combinados, paginación,
  resultados vacíos, registros inactivos, recarga tras edición, datos inválidos,
  autenticación y permiso académico.
- `SolicitudMatriculaHistorialHttpTest`: administrador y apoderado asociado,
  orden y aislamiento de eventos, historial vacío, rechazo a terceros, vínculos
  y registros inactivos, roles, permisos, 401 y 404.

Son **pruebas de integración HTTP**, con MockMvc, servicios y repositorios
reales sobre H2. El JWT se simula con Spring Security Test; no prueban la firma
del token ni reemplazan una prueba manual del login. Cada escenario usa una
transacción que se revierte; no se conecta a la base compartida de AWS.

Desde `backend`:

```powershell
.\mvnw.cmd -B -ntp -pl controller -am '-Dtest=HorarioFiltrosHttpTest,SolicitudMatriculaHistorialHttpTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd clean verify
```

Reportes:

- JUnit: `controller/target/surefire-reports/`.
- JaCoCo: `controller/target/site/jacoco-aggregate/index.html`.

Antes de marcar las historias completas, integrar y probar el frontend con la
API actualizada. La publicación en Git no actualiza por sí sola el despliegue.

## Resultado de la verificación del 30 de octubre de 2026

`mvnw.cmd -B -ntp clean verify`: **BUILD SUCCESS**.

- Backend completo: 303 pruebas, 0 fallos, 0 errores, 0 omitidas.
- Nuevas pruebas de horarios: 18; nuevas pruebas de historial: 17.
- JaCoCo agregado, incluyendo controller: instrucciones 92,03 % (30176/32790),
  líneas 93,12 % (6266/6729), ramas 72,47 % (1666/2299).
- Se conserva el umbral existente de 90 % para instrucciones y líneas.
  La cobertura de ramas se informa por separado y no alcanza 90 %.
- No se redujeron umbrales ni se agregaron exclusiones de cobertura.
- Estas cifras corresponden al backend con estos cambios locales; no acreditan
  una validación manual del frontend ni del entorno MySQL/desplegado.
