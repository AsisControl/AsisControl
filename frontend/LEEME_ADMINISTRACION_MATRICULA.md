# AsisControl — Administración y matrícula (incremento 1.3)

Fecha: 01/10/2026. Estos paquetes incluyen el trabajo del equipo, la reconstrucción de accesos centralizados y los cuatro apartados de este incremento. Son carpetas completas de código fuente; deben actualizarse frontend y backend juntos.

## Base conservada

- Repositorio: https://github.com/arom12321/AsisControl.git.
- Frontend: `feature/frontend-inicial`, commit `5c7a6b955a33c2ce5a8ca054d6db50be98124332`; reconstrucción anterior `aff6202`.
- Backend: `feature/backend-inicial`, commit `c606cf6249bd1b4ea4298b8020a8dd23b5702f89`; reconstrucción anterior `ab8536b`.
- Las ramas remotas se revisaron antes de implementar. Los cambios de este incremento están en ramas locales y no se han publicado en GitHub.
- Se conservan el login, menús y header con nombre y rol del coordinador; también las fichas, usuarios, Crear acceso centralizado, JWT, cambio obligatorio de contraseña y auditoría.
- Se usa el GUI propio de AsisControl (`GUI(1).pdf`): Inter local, azul institucional, formularios, tablas, tarjetas y tamaños adaptables. Los entregables de otros proyectos son referencias y no aportan reglas del caso.
- Se conserva Java 21 del POM del equipo y Spring Boot 4.1.1. El documento arquitectónico que menciona Java 24 debe reconciliarse con el equipo; este paquete no cambia su versión de Java.

## Qué se implementó

| Apartado | Comportamiento | Trazabilidad |
|---|---|---|
| Configuración académica | Año en borrador, períodos con fechas y orden, activación/cierre; grados anuales y capacidad sugerida; secciones individuales, capacidad, habilitación y vacantes calculadas | US-05, US-06, parcialmente |
| Roles y permisos | Consulta de los cuatro roles y permisos efectivos; servidor rechaza crear, modificar y eliminar roles por los endpoints ordinarios | CA-US-03-01 y 02; alcance de seguridad previo conservado |
| Logs del sistema | Registro automático de errores inesperados y problemas técnicos de formato/integridad; filtros de fecha, severidad, componente, correlación y estado; seguimiento con actor, fecha, observación e historial | US-28 |
| Gestión de matrícula | Pestañas Solicitudes, Matrículas y Fichas; selección anual de grado y sección; apoderados compartidos con la ficha; revisión, observación, rechazo, finalización, anulación/retiro y consultas de historial | US-11; asociaciones de US-10; preparación administrativa parcial de US-40 |

## Actualizar como venías haciendo

1. Detén las terminales del frontend y del backend con Ctrl+C. Conserva una copia de las carpetas que ya funcionan.
2. Extrae `Actualizacion_Frontend_Administracion_Matricula.zip` dentro de `AsisControl-prueba`. Abre la carpeta `frontend` extraída.
3. Copia su contenido directamente dentro de `asiscontrol-frontend`, sin crear una carpeta frontend adicional. Reemplaza `src` por la versión completa. Copia también `public`, `tests`, `package.json`, `package-lock.json` y los archivos de configuración. Conserva tu `.env.local` con la dirección real del backend.
4. En una terminal abierta en `asiscontrol-frontend`, ejecuta:

```powershell
npm ci
npm run dev
```

5. Extrae `Actualizacion_Backend_Administracion_Matricula.zip`. Copia el contenido de su carpeta `backend` dentro de tu backend actual. Mantén los módulos `entity`, `dto`, `repository`, `service`, `controller` y el `pom.xml` padre. Conserva tus variables de entorno, datos y almacenamiento externo.
6. Si utilizas MySQL existente, revisa la migración indicada más abajo antes de iniciar. En el perfil local H2, Hibernate crea el esquema temporal automáticamente.
7. Desde la carpeta `backend`, con Java 21 o un JDK compatible con el POM, ejecuta:

```powershell
mvn clean install
mvn -pl controller spring-boot:run
```

8. Abre http://localhost:3000 y entra como administrador. La dirección predeterminada del backend es http://localhost:8080. Para otra dirección configura `NEXT_PUBLIC_API_BASE_URL` con el origen, sin `/api` al final, y reinicia Next.js.

No se incluyen dependencias instaladas ni archivos de compilación. Si la base está vacía, utiliza las variables `BOOTSTRAP_ADMIN_*` que ya usabas; el ejemplo para una prueba nueva se conserva en la guía anterior. El primer acceso obliga a cambiar la contraseña. No uses usuarios simulados del README original.

## Preparar y demostrar el avance

1. En Administración → Configuración académica, crea un año con fechas reales de la institución. En su tarjeta, agrega los períodos con nombres, orden y fechas; no se obliga a cuatro períodos ni se inventan sus fechas.
2. Habilita un grado y configura su capacidad sugerida. Crea cada sección y su capacidad. La suma habilitada se muestra por grado.
3. Activa el año. Solo puede haber un año activo. Los períodos se activan y cierran individualmente; solo uno puede estar activo. Antes de cerrar el año deben estar cerrados todos sus períodos.
4. En Matrícula → Fichas de estudiantes, registra o utiliza una ficha existente.
5. En Administración → Usuarios → Crear acceso, registra el apoderado si aún no existe, o asigna otro acceso por rol a una persona existente. El flujo de acceso sigue centralizado.
6. En Matrícula → Solicitudes → Nueva solicitud, elige el estudiante, grado y sección del año activo. Guarda el borrador. Abre Ver y gestionar, asocia un apoderado existente si corresponde, envía la solicitud e inicia revisión.
7. En revisión puedes seleccionar otra sección del mismo grado/año, observar indicando qué subsanar, rechazar con motivo o finalizar la matrícula. Las observadas pueden editarse y enviarse de nuevo.
8. Finalizar matrícula crea una matrícula ACTIVA y cambia la solicitud a MATRICULA_FINALIZADA en una misma transacción. Solo entonces se consume la vacante; el pago no bloquea este MVP. Con la sección llena, otra finalización se rechaza.
9. En Matrículas puedes consultar historial o anular/retirar/finalizar una matrícula ACTIVA con motivo. El registro y sus eventos se conservan. Las anuladas y retiradas liberan vacante y no se reactivan por este flujo; puede iniciarse otra solicitud si el año sigue activo y hay vacante.
10. Consulta Administración → Roles y permisos: hay cuatro columnas y no hay controles de edición. Revisa Auditoría para las operaciones de negocio y Logs del sistema para errores técnicos.

Las vacantes son `capacidad máxima − matrículas ACTIVA habilitadas`. Las solicitudes no ocupan una vacante. La UI muestra valores de consulta; el servidor los recalcula al confirmar. Los cambios sensibles utilizan versión: si otro usuario modificó el registro, actualiza los datos antes de reintentar.

Las solicitudes siguen BORRADOR → ENVIADA → EN_REVISION → MATRICULA_FINALIZADA, con OBSERVADA → ENVIADA, RECHAZADA y CANCELADA según el origen. Las matrículas siguen ACTIVA → RETIRADA / FINALIZADA / ANULADA. Los estados finales no se reabren ordinariamente.

## MySQL existente

El perfil `mysql` utiliza `ddl-auto=validate`: las migraciones son manuales. Sobre una copia de la base, aplica `migrations/001_accesos_por_rol.sql` si todavía no lo hiciste y después `migrations/002_administracion_matricula.sql`.

La segunda migración crea configuración anual, períodos, grados e historial de seguimiento técnico; añade versión a errores y sustituye la unicidad permanente estudiante/año por la unicidad de la matrícula vigente. Conserva las matrículas anteriores y las restricciones por código y solicitud. Detecta los nombres físicos de las tablas heredadas.

No se ha ejecutado este SQL sobre MySQL en este entorno. Verifica los nombres físicos, índices y tipos en una copia antes de usarlo con la base del equipo. El modelo y la concurrencia se probaron con H2 en modo MySQL. Los nombres lógicos de entidades/tablas conservan PascalCase del estándar; la estrategia física existente de Spring genera snake_case, por lo que se mantiene esa compatibilidad en la migración.

Las secciones heredadas se conservan con su año y grado. Para poder matricular con ellas, crea ese año con sus fechas reales, configura los grados correspondientes y activa el año. No crees otra sección con el mismo nombre/grado/año. No se importan años ni se activan contextos automáticamente.

El perfil `local` del equipo usa H2 en memoria y pierde los datos al reiniciar. Para conservar información usa el perfil MySQL existente y sus variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET`.

## Contratos que cambiaron

- Nuevos endpoints de `/api/configuracion-academica` para años, períodos y grados.
- Crear sección acepta `motivo`; es obligatorio en año activo. Editar sección exige `version` y `motivo`.
- `PUT /api/secciones/{id}/estado` exige `activo`, `version` y `motivo`. El borrado ordinario está bloqueado en este incremento.
- Actualizar matrícula exige `estado`, `version` y `motivo`. Borrados de solicitudes y matrículas están bloqueados.
- Matrícula y solicitud ofrecen `GET /{id}/historial`, restringido a administrador.
- Actualizar error técnico exige `estado`, `observacionSeguimiento` y `version`; `GET /api/auditoria/errores/{id}/seguimientos` muestra el historial.
- La UI normaliza tanto `PageResponse` como las variantes Spring Page del backend para mantener paginación correcta.

## Límites y decisiones pendientes de validación

- El incremento no completa todas las historias relacionadas: quedan días no lectivos y su integración con asistencia (CA-US-05-18), generación/confirmación masiva de secciones (CA-US-06-03, 04, 08), y eliminación física de una sección borrador sin uso (CA-US-06-14). Aquí las secciones se crean individualmente y se conservan al inhabilitarse.
- La inhabilitación de una sección exige que no tenga solicitudes, matrículas ni asignaciones, incluso históricas. Es una protección más restrictiva de este incremento y debe validarse si se desea permitir retirar oferta utilizada.
- La vista administrativa prepara solicitudes para la demostración. El portal de solicitud del apoderado, convocatoria/plazos y toda la US-40 todavía no están implementados. El backend de este incremento reserva las mutaciones de matrícula al administrador. La comprobación de duplicidad conserva una sola solicitud ABIERTA; permitir otra tras rechazo/anulación requiere reconciliar la redacción de CA-US-40-04.
- Se puede asociar un apoderado existente sin parentesco obligatorio. El ciclo de vida de asociaciones con finalización/reactivación motivada de US-10 sigue pendiente; no hay otro formulario de acceso.
- Solo ACTIVA ocupa vacante; los estados terminales se conservan. Validar con el profesor cualquier política adicional de reapertura, traslado y regularización.
- El 30 inicial de capacidad es una sugerencia editable, no un requisito validado. Grados 1–5, capacidad sugerida 1–100 y años 2000–2100 son límites de este incremento; deben revisarse si el caso requiere otros.
- Los cuatro roles se muestran y sus mutaciones ordinarias se rechazan. No se eliminan cuentas ni roles heredados adicionales de la base del equipo; el nuevo flujo e interfaz trabajan con los cuatro acordados. Este avance no pretende certificar todos los criterios de seguridad de todo el backlog.
- Las fechas de filtros de logs incluyen el día completo en America/Lima. Los mensajes del registro automático excluyen valores de formularios, contraseñas, tokens y detalles SQL. La correlación aparece solo en la consulta administrativa técnica, no en los mensajes de error de las pantallas.
- La verificación visual en navegador queda pendiente; el build, contratos, rutas y llamadas HTTP fueron verificados. La recuperación automática por correo y los módulos ajenos a este avance continúan pendientes.

## Verificación de esta entrega

- Backend: 44 pruebas automáticas aprobadas, incluidas dos transacciones independientes sobre la última vacante y reversión sin auditoría de éxito falsa.
- Frontend: 10 pruebas de contrato, ESLint y build de producción aprobados.
- Integración HTTP: 95 comprobaciones reales, incluyendo login, acceso por rol, configuración, estados, vacantes, apoderados, motivos, versiones, logs y cierre histórico.
- Rutas y fuente local: siete comprobaciones HTTP aprobadas.

En una terminal de frontend puedes repetir `npm run test`, `npm run lint` y `npm run build`; en backend, `mvn test`. Las pruebas del backend usan el perfil test con datos aislados H2.
