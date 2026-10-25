> Integración v08, 25/10/2026: base develop 7ce01a5. Conserva sesiones, carga masiva y formularios del equipo; la ficha utiliza V5. Consultar INTEGRACION_FICHA_DEVELOP.md y RESULTADOS_VERIFICACION.json del paquete. Las cifras y referencias que siguen son históricas.

# Estado vigente: revisión v0.4 del 22/10/2026

Consultar [REVISION_ESTANDAR_v04.md](REVISION_ESTANDAR_v04.md) para la organización del código, los resultados y los paquetes actuales. Las reglas funcionales se mantienen en [CORRECCIONES_FICHA_v03.md](CORRECCIONES_FICHA_v03.md). El contrato de backend se explica en `backend/docs/BE2_FICHA_VALIDACIONES.md`.

El contenido siguiente se conserva como **registro histórico de v0.2**. Sus afirmaciones de backend sin cambios, nacimiento hasta hoy y formatos anteriores no describen las versiones v0.3/v0.4.

---

# FE2 — Ficha institucional de estudiante (US-09)

Versión del avance: 0.2 · 21/10/2026. Rama local: `feature/fe2-ficha-estudiante`.
Base inicial: `main`, commit `cb126b02c881d2ba55b7fbd2ee5efa7610e04192`.
Base integrada actual: `develop`, commit `5f26d465aae27dea0d8ba5e90a684855f7f4fcc3` (PR #8).

La rama ya incorpora el PR #6 (main → develop), el PR #7 de estructura académica y el PR #8 con los cambios recientes de frontend. Se conservó la autoría del equipo mediante merge. El aporte propio de FE2 se compara ahora contra este `develop`; no es necesario volver a sincronizar las dos versiones de base indicadas aquí.

Para importar y publicar esta rama desde tu cuenta, seguir `PUBLICAR_FE2_US09.md`. El ZIP es una copia de código para ejecutar y no crea automáticamente una rama Git en tu PC.

## Estado real de la entrega

Este avance implementa la interfaz FE2 y sus pruebas. **US-09 sigue abierta:** el backend de la base todavía no implementa varias decisiones aceptadas de OBS-033 a OBS-040. Las pruebas de API de frontend utilizan respuestas simuladas; no acreditan persistencia, auditoría ni autorización de la API real. La rúbrica exige integración: no presentar este avance como una historia terminada.

Se conserva la organización `core/shared/modules`, los estilos escolares existentes y el header del equipo. Los PDFs de Galería El Rey Virtual / Grupo 3 son referencias documentales ajenas al caso y no se usan como GUI escolar.

## Cómo abrir y revisar en Windows

1. Extraer el proyecto completo en una carpeta nueva. El directorio raíz contiene `frontend/` y `backend/`, como GitHub.
2. En VS Code: **Archivo → Abrir carpeta** y seleccionar ese directorio raíz.
3. Abrir **Terminal → Nueva terminal** y ejecutar, desde la raíz:

```powershell
cd frontend
npm.cmd ci
npm.cmd test
npm.cmd run lint
npm.cmd run build
```

Para iniciar la interfaz:

```powershell
Copy-Item .env.example .env.local
npm.cmd run dev
```

Crear `.env.local` solo si aún no existe. Su `NEXT_PUBLIC_API_BASE_URL` debe apuntar al backend del equipo (ejemplo local: `http://localhost:8080`). Abrir `http://localhost:3000`, ingresar con un administrador existente y entrar en **Matrícula → Fichas de estudiantes**. También existe la ruta `/estudiantes`.

No se incluyen credenciales ni un backend simulado en la aplicación. El backend de este paquete incorpora los cambios del equipo presentes en el `develop` indicado; no hay modificaciones propias de FE2 sobre backend. Para ejecutarlo usar su README y la configuración de base de datos del equipo. La API de estudiantes conserva el contrato anterior para las consultas existentes de fichas habilitadas; **el alta y la edición con el contrato nuevo requieren los cambios BE2 siguientes**.

Pruebas solo de FE2: `npm.cmd run test:students`. No registran horas reales de integrantes ni actualizan el tablero remoto.

## Cambios implementados

- Listado paginado, búsqueda por nombre/código/documento y estados de carga, vacío, error y reintento. No se filtra solo la página visible ni se simulan resultados.
- Alta con nombres, primer apellido y fecha de nacimiento obligatorios; segundo apellido, sexo y contacto opcionales. Tipo y número de documento juntos o ambos nulos. DNI de ocho dígitos; CE/pasaporte alfanumérico de 8–12.
- Código institucional asignado por servidor; solo lectura en edición y excluido del cuerpo enviado.
- Fecha máxima inclusiva de hoy en `America/Lima`. Las fechas civiles se muestran sin desplazamiento por zona horaria.
- Detalle con estado, documento pendiente y fechas administrativas disponibles; apoderados existentes en consulta.
- Edición de identidad con motivo de 10–1000 caracteres, validaciones por campo, confirmación para descartar cambios y protección contra doble envío.
- Revisión explícita de homónimos: mostrar coincidencias, confirmar persona distinta y reenviar el mismo borrador con `confirmarHomonimia: true`. Cambiar cualquier dato invalida esa confirmación. Un documento duplicado nunca habilita la excepción.
- Confirmación motivada de inactivación, reactivación o eliminación, habilitada solo con acciones permitidas declaradas por el contrato nuevo de BE2. Si no se informan, las acciones no se muestran ni se envían.
- La gestión administrativa de fichas solo se monta con rol Administrador. El alcance por registro y los otros roles siguen siendo responsabilidad de BE1/BE2.

## Contrato que debe cerrar FE2 con BE2

Las reglas de negocio fueron aceptadas en la matriz. **Los nombres JSON, códigos nuevos, metadatos y la ruta PATCH de esta sección son una propuesta técnica de integración**, pendiente de confirmación e implementación de BE2. No son endpoints acreditados como existentes. Se preservan los nombres actuales para reducir cambios de integración:

| Nombre en las decisiones | Nombre JSON usado |
|---|---|
| primerApellido | `persona.apellidoPaterno` |
| segundoApellido | `persona.apellidoMaterno` |
| correoContacto | `persona.correo` |
| Código institucional de respuesta | `codigoAlumno` |

### Alta y actualización

`POST /api/alumnos` → 201 con `Student`. `PUT /api/alumnos/{id}` → 200 con `Student`.

```json
{
  "persona": {
    "nombres": "Lucía",
    "apellidoPaterno": "Flores",
    "apellidoMaterno": null,
    "tipoDocumento": null,
    "numeroDocumento": null,
    "fechaNacimiento": "2012-05-14",
    "sexo": null,
    "correo": null,
    "telefono": null,
    "direccion": null,
    "nacionalidadId": null
  }
}
```

En correcciones sensibles se agrega `motivo`. Solo después de confirmar un homónimo se agrega `confirmarHomonimia: true`. Si el servidor devuelve `version`, se conserva en la actualización. No se envían `codigoAlumno`, `activo`, contraseñas, matrícula ni vínculos familiares.

BE2 debe generar y garantizar de forma atómica la unicidad del código `ESTU-000001`, comprobar duplicados e identidad en servidor, validar motivo, conservar datos compartidos de Persona y auditar la modificación en la misma transacción. Crear una ficha no debe crear acceso ni matrícula.

Para el contacto se conservaron los límites existentes (correo 150, dirección 250, teléfono 7–20 con dígitos/espacios/`+()`/guion). Para el segundo apellido opcional se propone máximo 100, coherente con los otros nombres. Confirmar estos límites técnicos con BE2; no se presentan como nuevos requisitos expresos del backlog.

### Errores y homonimia

Se conserva el formato de `ApiError` existente: `status`, `code`, `message` y `fieldErrors: [{field, message}]`. Los campos anidados utilizan, por ejemplo, `persona.nombres`. Los mensajes de validación no deben incluir valores personales ni cuerpos de petición.

Propuesta para advertencia de homonimia:

```json
{
  "status": 409,
  "code": "HOMONIMIA_DETECTADA",
  "message": "Se encontraron posibles coincidencias",
  "fieldErrors": [],
  "details": {
    "coincidencias": [
      { "id": 1, "codigoAlumno": "ESTU-000001", "nombreCompleto": "Lucía Flores", "fechaNacimiento": "2012-05-14" }
    ]
  }
}
```

La UI no habilita confirmación si faltan los datos mínimos para revisar las coincidencias. Normalizar sin tildes, en mayúsculas y con espacios simples en servidor; conservar la escritura de los nombres. `DOCUMENTO_DUPLICADO` es un rechazo definitivo y no se trata como homonimia. `VERSION_DESACTUALIZADA` pide volver a consultar la ficha. Los códigos técnicos no se añaden al mensaje visible.

### Ciclo de vida seguro

Propuesta de respuesta ampliada en GET/POST/PUT:

```json
{
  "id": 42,
  "codigoAlumno": "ESTU-000042",
  "activo": true,
  "version": 3,
  "accionesPermitidas": { "editar": true, "inactivar": true, "reactivar": false, "eliminar": false }
}
```

El ejemplo es parcial: también se conservan `persona`, `apoderados` y fechas administrativas. Las acciones deben calcularse en servidor para el usuario y la ficha; son una ayuda de interfaz, y no sustituyen revalidar cada petición.

| Acción | Propuesta de petición | Comportamiento obligatorio BE2 |
|---|---|---|
| Inactivar | `PATCH /api/alumnos/{id}/estado`, `{activo:false,motivo,version?}` | Rechazar con matrícula vigente; conservar Persona/otros roles e historial; revocar únicamente acceso Estudiante; auditar. |
| Reactivar | Misma ruta, `{activo:true,motivo,version?}` | Habilitar perfil y auditar; cerrar con BE1/BE2 la política del acceso vinculado. |
| Eliminar | `DELETE /api/alumnos/{id}`, `{motivo,version?}` → 204 | Solo sin relaciones/uso según definición cerrada con equipo; auditar sin destruir el historial de auditoría. |

**No devolver `accionesPermitidas` antes de cambiar la semántica del servidor.** El DELETE actual llama `deactivateProfile(Persona)` y afecta todos sus accesos. La UI de este avance no ofrece ese DELETE cuando falta el contrato nuevo.

También falta permitir consulta histórica de fichas inactivas: el GET/listado actual utiliza `activo=true`. No se añadió un filtro visual que el servidor ignoraría. Confirmar si GET/listado devolverá ambos estados o aceptará un filtro explícito.

Dos interpretaciones siguen pendientes de cierre técnico/funcional: si reactivar el perfil reactiva automáticamente su acceso, y cómo definir «sin historial» para eliminación preservando el evento inicial de auditoría. La interfaz no inventa esas reglas.

## Trazabilidad por criterio (CA-US-09)

Todos los estados siguientes corresponden al frontend. Ninguna fila sustituye su prueba de aceptación integrada AT-US-09 del backlog.

| CA | Avance FE2 / prueba asociada | Pendiente para aceptación integrada |
|---|---|---|
| 01 | Alta mínima, validación y POST. Pruebas `CA-01/02`, `CA-01`, `CA-01/10`. | Código, estado inicial, unicidad y persistencia BE2. |
| 02 | Ambos campos de documento nulos; prueba `CA-01/02`. | Nulabilidad DTO/BD y documento pendiente en API. |
| 03 | Rechaza documento incompleto; prueba `CA-03`. | Rechazo equivalente por API directa. |
| 04 | Rechazo definitivo de duplicado; prueba `CA-04/23`. | Unicidad transaccional y consulta autorizada de coincidencia. |
| 05 | Fechas válidas <= hoy Lima; dos pruebas `CA-05`. | Misma fecha del servidor y validación por API directa. |
| 06 | Advertencia y datos mínimos de coincidencia; prueba `CA-06/07`. | Detección normalizada y respuesta 409 BE2. |
| 07 | Confirmación explícita y reinicio al editar; pruebas de contrato y DOM `CA-06/07`. | Revalidación final y auditoría BE2. |
| 08 | Detalle y render de datos/estado/fechas; dos pruebas `CA-08`. | Fechas de ficha y consulta real completa. |
| 09 | Búsqueda y paginación contra GET existente; prueba DOM `CA-09`, incluyendo retorno a página cero. | Verificar interfaz en navegador, datos persistidos, alcance y fichas históricas. |
| 10 | PUT, contacto opcional y datos conservados; pruebas `CA-01/10`, `CA-10`, `CA-10/11/20/21`. | Contrato de actualización, persistencia y conservación de Persona. |
| 11 | Código sin campo editable ni en payload; pruebas `CA-08/11`, `CA-10/11/20/21`. | Inmutabilidad ante API directa. |
| 12 | Motivo obligatorio en identidad; prueba `CA-12/13`. | Validación obligatoria BE2. |
| 13 | Motivo 10–1000 y datos de actualización; `CA-12/13`, `RN-172`. | Auditoría con actor, anterior/nuevo y hora servidor. |
| 14 | Diálogo y PATCH solo con acción permitida; `CA-14/17`, `CA-14/17/18`. | Transacción y efecto exclusivo sobre acceso Estudiante. |
| 15 | Rechazo 409/422 conserva resultado; prueba `CA-15/19`. | Validar matrícula vigente en servidor. |
| 16 | Consulta de estado Inactiva preparada en detalle. | Rechazar nuevos usos en matrícula/otros módulos; GET histórico BE2. |
| 17 | Diálogo y PATCH de reactivación; prueba `CA-14/17`. | Reactivación real y auditoría; cerrar efecto sobre acceso. |
| 18 | Confirmación motivada de DELETE solo con contrato nuevo; prueba `CA-18`. | Eliminación real protegida y definición de historial. |
| 19 | El servidor decide acción y revalida; pruebas `CA-14/17/18`, `CA-15/19`. | Comprobar relaciones por API directa y bajo concurrencia. |
| 20 | Alta sin campos de acceso; prueba `CA-10/11/20/21`. | Verificar que servidor no crea credenciales. |
| 21 | Alta sin campos de matrícula; misma prueba. | Verificar que servidor no crea matrícula. |
| 22 | Gestión reservada a Administrador; prueba `CA-22`. | Consultas por registro/proyección docente, propia y familiar BE1/BE2; navegación FE1. |
| 23 | Sin logs de formularios, sin códigos visibles, conflicto sin documento; `CA-04/23`, `FE2-05`. | Revisar logs y respuestas reales de todos los roles. |
| 24 | Motivo enviado donde corresponde; `CA-12/13`, `CA-14/17`, `CA-18`. | Auditoría transaccional de todas las acciones BE2. |

## Revisión con FE5 e integración

Los cambios de FE2 quedan dentro de `modules/students`. Los ajustes de soporte requieren revisión FE5: `core/api/client.ts` ahora conserva `status/code/fieldErrors/details` en un error que sigue extendiendo `Error`; `package.json` incorpora las pruebas del módulo a `npm test`. Se añade únicamente la dependencia de desarrollo `jsdom@26.1.0` y se actualiza el lockfile para probar interacciones reales de componentes React en DOM simulado. No se cambia `shared/forms/person-fields.tsx`, para preservar docentes/apoderados.

## Recorridos manuales pendientes

1. Administrador: buscar por nombre, documento y código; comprobar página siguiente/anterior, búsqueda sin resultados, error de red y reintento. Confirmar que una búsqueda nueva empieza en la primera página.
2. Registrar con los tres datos mínimos y sin documento/contacto; comprobar código generado, ficha Habilitada y ausencia de matrícula/acceso automáticos en la base real.
3. Rechazar tipo o número solos, DNI de siete dígitos, fecha futura y documento duplicado. Permitir nacimiento igual a hoy Lima y CE/pasaporte válido.
4. Corregir identidad: rechazar motivo vacío o de nueve caracteres; aceptar uno válido y consultar auditoría. Corregir solo contacto sin exigir motivo.
5. Revisar homónimos y confirmar persona distinta. Cambiar un dato debe invalidar la confirmación previa; un documento duplicado debe continuar bloqueado.
6. Con API nueva y acciones habilitadas: probar inactivación, rechazo con matrícula, consulta histórica, reactivación y eliminación protegida; conservar los otros roles.
7. En móvil (390 px) y escritorio (1366 px), comprobar legibilidad, tabla/formulario sin desbordamiento, teclado, foco de errores y diálogos. El header del equipo debe conservar nombre y rol.

La ejecución de estos recorridos en navegador y su evidencia visual sigue pendiente. No se consiguió iniciar el navegador en el entorno de esta entrega; las pruebas DOM y de render no acreditan maquetación ni el flujo con MySQL real.

## Verificación realizada el 21/10/2026

| Comprobación | Resultado |
|---|---|
| Instalación limpia desde lockfile (`npm ci --ignore-scripts --no-audit --no-fund`) | Correcta, sin cambiar versiones del equipo. |
| `npm test` | 44/44: 12 de regresión existentes, 23 de reglas/contrato/render y 9 de interacción DOM. |
| `npm run lint` | Correcto. |
| `npm run build` | Correcto; TypeScript y generación de rutas incluidos. |
| Aporte propio FE2 en backend frente a develop | Ningún archivo. Se incluyen los cambios del equipo integrados hasta PR #8. |
| Base actual verificada | `develop` en `5f26d465aae27dea0d8ba5e90a684855f7f4fcc3`, incorporado sin conflictos de archivos. |
| Arranque de producción y HTTP | `/`, `/matricula`, `/administracion/usuarios` y `/recuperar-contrasena` responden HTTP 200. No equivale a probar login o persistencia. |
| Publicación en GitHub | Bloqueada: la integración devolvió 403 al intentar crear la rama. El avance se conserva en rama local y en el paquete descargable. |
| Navegador, MySQL y auditoría real | Pendientes; no acreditados por las respuestas HTTP simuladas de las pruebas. El entorno de verificación tiene Java 17; no se ejecutaron pruebas del backend que exige Java 21. |

La dependencia `jsdom` solo se usa en desarrollo/pruebas. La aplicación continúa llamando a la API real con el cliente existente y no incorpora un modo de demostración con datos ficticios.

Antes del merge del avance funcional: completar contrato con BE2, ejecutar todas las AT de US-09 contra MySQL/API real, revisar los flujos y roles, adjuntar evidencia al PR, mantener backlog/esfuerzo real actualizados y obtener una revisión aprobada (OBS-055). La actualización de esta rama con `develop` ya fue realizada; comprobar nuevamente la base si el equipo publica cambios posteriores.

## Control de cambios

| Versión | Fecha | Cambio |
|---|---|---|
| 0.1 | 21/10/2026 | Interfaz FE2 sobre main; reglas OBS-033–040; pruebas y contrato técnico propuesto con brechas BE2 explícitas. |
| 0.2 | 21/10/2026 | Integra develop hasta `5f26d46` preservando historial del equipo. Repite las 44 pruebas, lint y build; verifica cuatro rutas HTTP. Incluye guía de publicación e importación de la rama. |
