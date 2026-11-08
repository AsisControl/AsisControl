# US-12 · Compromisos de pago

## Trazabilidad y alcance

- Backlog: **US-12 / MAT-04**.
- Prioridad: **Must**. Iteración: **I2**.
- Dependencias: **US-04, US-05, US-09 y US-11**.
- Planificación indicada: opcional para Sprint 2 (26 de octubre al 6 de noviembre), según capacidad.
- Fuente de aceptación: `historias-y-pruebas-de-aceptacion_v4.md`, sección US-12, criterios CA-US-12-01 a CA-US-12-13.
- Aclaración posterior: `Explicacion_Diagramas_Estado_AsisControl.md`, sección de
  compromisos, confirma anulación únicamente sin dinero aplicado y sin borrar el historial.
- Rama de trabajo: `feature/us12-compromisos-pago`, creada sobre `c67b686`, la
  integración local que ya contiene las correcciones de la ficha. Para resolver
  la integración del PR #46 se incorporó `origin/develop` en `1c21df0`, conservando
  sus avances de cursos y sesiones de asistencia junto con la US-12.

La pantalla **Finanzas → Compromisos de pago** permite emitir obligaciones
manualmente, consultarlas y modificar o anular las que todavía no tengan pagos
aprobados. Cada compromiso pertenece a una matrícula vigente y se expresa en
soles. El procesamiento de pagos corresponde a US-13 y la gestión completa de
morosidad corresponde a US-44.

## Reglas de uso

1. Seleccionar una matrícula vigente y completar concepto, número de cuota,
   importe y vencimiento. El sistema asigna el código del compromiso.
2. No emitir otro compromiso activo del mismo estudiante, año, concepto y cuota.
3. Consultar por estudiante, año y estados. El estado administrativo
   **Activo/Anulado** se muestra separado de la situación financiera calculada.
4. Para cambiar importe o vencimiento, registrar el motivo. Un compromiso con
   alguna transacción aprobada no admite modificaciones.
5. Para anular, registrar el motivo y confirmar. El registro se conserva y
   continúa disponible para consulta.
6. Las operaciones ordinarias no permiten eliminar compromisos.

Al salir de un formulario con cambios, la confirmación aparece en una ventana
independiente. «Seguir editando», Escape y el cierre de esa confirmación conservan
los campos, la búsqueda y la matrícula seleccionada. «Buscar matrícula» se presenta
como un botón con borde, estados de interacción y altura mínima de 44 píxeles.

El documento de aceptación deja consultas pendientes al Product Owner sobre
pagos, recargos, descuentos y catálogo de conceptos. La aclaración posterior
de los diagramas confirma la anulación sin dinero aplicado. Esta implementación
bloquea la anulación cuando existen pagos aprobados. No agrega recargos,
descuentos ni devoluciones.

## Permisos y organización

- `PAGOS_LEER`: acceso a Finanzas y consultas autorizadas.
- `PAGOS_ESCRIBIR`: emisión, modificación y anulación en el servidor; la interfaz
  presenta estas acciones únicamente a quienes disponen del permiso.
- La consulta familiar conserva la restricción a estudiantes asociados.
- El módulo de interfaz se encuentra en `src/modules/finance/`. Los servicios
  usan el cliente HTTP compartido y los estilos propios usan CSS Modules.
- El backend conserva la separación entre controlador, DTO, servicio,
  repositorio y entidad, con auditoría centralizada.

## Comprobación manual

Preparar un año académico configurado, un estudiante y una matrícula activa.
Ingresar con una cuenta autorizada para consultar y gestionar pagos.

| Caso | Pasos | Resultado esperado |
|---|---|---|
| Emitir | Seleccionar matrícula, concepto «Pensión», cuota 1, S/ 350.50 y vencimiento; confirmar. | Compromiso activo con código, estudiante y año correctos. |
| Obligatorios | Intentar emitir sin concepto, cuota, importe o vencimiento. | Se identifican los campos que necesitan completarse. |
| Importe | Probar cero, negativo o más de dos decimales. | Se rechaza sin crear una obligación. |
| Matrícula | Intentar emitir con una matrícula que haya dejado de estar vigente. | El servidor rechaza la emisión. |
| Duplicado | Repetir estudiante, año, concepto y cuota de un compromiso activo. | Se informa la coincidencia y se bloquea la creación. |
| Consultar | Buscar por estudiante y aplicar filtros. | Se muestran cuota, importe, vencimiento y ambos estados. |
| Modificar | Abrir un compromiso sin pagos, cambiar importe o fecha y registrar motivo. | Se conserva el cambio y queda auditado. |
| Motivo | Intentar modificar o anular con el motivo vacío o solo espacios. | Se rechaza la operación. |
| Anular | Anular un compromiso sin pagos aprobados y confirmar el motivo. | Se muestra Anulado y continúa disponible en las consultas. |
| Reemitir | Emitir la misma combinación después de anular la anterior. | Se permite una nueva obligación sin borrar la anterior. |
| Pago aprobado | Consultar un compromiso con una transacción aprobada e intentar modificar o anular. | El servidor bloquea el cambio. |
| Permisos | Abrir Finanzas con permiso de lectura, sin escritura. | Puede consultar; no dispone de acciones para cambiar datos. |
| Auditoría | Consultar Administración → Auditoría tras cada operación. | Actor, fecha, motivo y valores anteriores/nuevos corresponden a la operación. |

## Actualización local

Los cambios requieren recompilar el backend y ejecutar su versión actualizada.
La nueva estructura de datos utiliza una migración adicional; no se deben editar
las migraciones ya aplicadas ni ejecutar SQL manualmente en una base compartida.

La migración `V8__compromisos_pago_administrativos.sql` conserva los importes y
monedas históricos, recupera la consulta de los compromisos que se anularon con
el mecanismo anterior y no inventa números de cuota. Un compromiso anterior sin
cuota registrada bloquea una nueva emisión coincidente del mismo estudiante,
año y concepto hasta su revisión. Los registros históricos en otra moneda se
presentan en su moneda original y no permiten modificar el importe o la fecha;
pueden anularse con motivo si no tienen pagos aprobados. Las emisiones nuevas
son exclusivamente en PEN.

Durante la integración del PR #46 se renombró la migración propia de US-12 de
V6 a V8, sin cambiar su contenido. Se conservan las migraciones V6 de cursos y
V7 de sesiones que ya existen en `develop`. Este cambio corresponde a la rama
de trabajo local, cuya migración de US-12 todavía no se ha aplicado a una base
persistente compartida; no se deben renumerar migraciones ya aplicadas.

Antes de aplicar las migraciones integradas a una base persistente con datos,
comprobar la precondición de V7: presupone que `AsistenciaAlumno` no contiene
asistencias históricas, porque añade una relación obligatoria con la sesión sin
completar registros anteriores. Una RDS nueva sin ese historial cumple esta
precondición. Si ya existen asistencias, coordinar su adaptación antes del
despliegue, conservando la migración del equipo.

En el perfil local con H2 en memoria, reiniciar el backend borra los datos de
prueba. En una base persistente, respaldar los datos y aplicar el despliegue
habitual del proyecto. El servidor de desarrollo del frontend detecta los
cambios de la interfaz; una instalación con archivos compilados necesita una
nueva compilación.

Para actualizar la ejecución local, detener primero el backend anterior cuando
corresponda y recompilarlo. Desde la carpeta `backend`, en la terminal que conserva
tu configuración local:

```powershell
.\mvnw.cmd package -DskipTests
java -jar .\controller\target\asiscontrol-controller-1.0.0-SNAPSHOT.jar
```

Desde `frontend`, iniciar la interfaz con `npm.cmd run dev` y abrir **Finanzas**.
Si no aparecen matrículas al emitir, comprobar primero las precondiciones del
año académico y de la matrícula; no se emiten compromisos directamente desde
una ficha sin matrícula.

## Verificación previa a la integración de develop · 4 de noviembre de 2026

| Comprobación | Resultado |
|---|---|
| Backend, `mvn verify` | 259 pruebas aprobadas; sin fallos, errores ni omitidas. |
| Cobertura backend | 93,23 % de líneas; supera el umbral del proyecto del 90 %. |
| Migración V8 (anteriormente V6) | SQL exacto ejecutado en H2 con modo MySQL; incluye registros antiguos, moneda original, anulación previa, espacios y tabulaciones. Se conserva el contenido al resolver la integración del PR #46. |
| Frontend, `npm run test:coverage` | 294 pruebas aprobadas en 21 archivos; cobertura de líneas 98,42 % y ramas 93,73 %. |
| Módulo Finanzas | 18 pruebas de interacción aprobadas; cubre emisión, consulta, cambios, motivos, errores, permisos, bloqueo durante guardado y registros históricos. |
| `npm run lint` | Correcto, sin errores ni advertencias. |
| `npm run build` | Compilación y comprobación de TypeScript correctas. |
| Revisión visual | Listado y formularios con datos ficticios; emisión y anulación en la vista de revisión, móvil de 390 × 844 sin desbordamiento horizontal del diálogo y código largo legible. |
| `git diff --check` | Correcto. |

La migración aún requiere comprobarse sobre un motor MySQL real antes del
despliegue en ese entorno. No se ejecutó una carrera de solicitudes paralelas;
la protección usa bloqueos y una restricción única, cuya aplicación sí se probó
en la base de pruebas. La revisión visual utilizó datos ficticios y las pruebas
HTTP del backend verificaron las reglas sobre la aplicación real en H2.

La revisión del estándar abarcó los archivos modificados para US-12 y su
integración con pagos, auditoría, navegación y paneles. No constituye una
certificación de todo el repositorio ni una validación del despliegue remoto.

## Verificación del backend al integrar develop en el PR #46

| Comprobación | Resultado |
|---|---|
| Pruebas integradas del backend | 379 aprobadas; sin fallos, errores ni omitidas. Incluyen los 14 escenarios HTTP de US-12 y la ejecución exacta de V8 en H2 con modo MySQL. |
| Cobertura agregada y umbral | Instrucciones 92,96 %, líneas 93,68 % y ramas 75,25 %. La verificación del umbral del 90 % de instrucciones y líneas fue correcta. |
| Empaquetado aislado | `mvn package -DskipTests` correcto sobre una copia de las fuentes integradas, sin modificar la ejecución local abierta. |
| Contenido del paquete | JAR ejecutable con `BOOT-INF`, V6 de cursos, V7 de sesiones y V8 de compromisos; sin la antigua V6 de US-12. Las tres migraciones coinciden exactamente con sus fuentes. |

En la carpeta de trabajo habitual, `mvn verify` completó las 379 pruebas, pero
se detuvo al intentar renombrar el JAR durante el reempaquetado, por un archivo
en uso. Se conservaron los resultados de las pruebas y se ejecutaron por
separado el reporte agregado y la misma verificación de cobertura del proyecto.
El empaquetado se comprobó en la copia aislada, sin repetir las pruebas ni
detener el backend abierto o borrar sus datos de H2. El paquete aislado no se
ejecutó.

## Verificación del frontend al integrar develop en el PR #46

| Comprobación | Resultado |
|---|---|
| `npm run test:coverage` | 449 pruebas aprobadas en 32 archivos; todos los umbrales correctos. |
| Cobertura | Líneas y sentencias 98,46 %, ramas 93,89 % y funciones 95,74 %. |
| `npm run lint` | Correcto, sin errores ni advertencias. |
| `npm run build` | Compilación, comprobación de TypeScript y generación de páginas correctas. |
| Navegación integrada | Se conservan Finanzas, la vista de fichas por URL, Tareas con sus permisos de escritura y adjuntos, e historial de Horarios con `AUDITORIA_LEER`. |

Se añadieron tres pruebas de regresión sobre la navegación real de la aplicación
para proteger las rutas y los permisos afectados al resolver el conflicto.
La configuración de Vitest conserva un único tiempo máximo de 20 segundos por
prueba y un máximo de dos procesos; no se redujeron las aserciones ni los umbrales.

## Fechas de las pruebas financieras en CI

La primera ejecución de GitHub Actions tras resolver el conflicto detectó una
fecha de prueba calculada con la zona predeterminada del servidor. A las
04:40 UTC del 5 de noviembre todavía era 4 de noviembre en Lima: restar un día
a la fecha UTC produjo un compromiso que vencía ese mismo día en Perú, aunque
la prueba lo esperaba vencido.

Las pruebas financieras ahora calculan sus fechas con `America/Lima`, como las
reglas de pagos de la aplicación. Se mantiene la expectativa de vencido para
fechas anteriores y se añade una prueba HTTP que comprueba que un compromiso
que vence hoy sigue pendiente y queda fuera del filtro de vencidos.

Se reprodujo el fallo original con la JVM en UTC durante el horario en que UTC
y Lima tenían fechas diferentes. Con la corrección, `clean verify` en UTC aprobó
las 380 pruebas, el empaquetado y el umbral de cobertura (93,68 % de líneas y
92,96 % de instrucciones). La comprobación se realizó en una copia aislada de
las fuentes para conservar la aplicación local abierta y sus datos de prueba.
Las 24 pruebas financieras dirigidas también pasaron con la JVM en
`America/Lima`, sin fallos ni errores.

## Ajustes de presentación y referencias — 7 de noviembre de 2026

- Los filtros eliminan el margen inferior de sus etiquetas y usan controles de
  44 px de alto para que «Buscar» quede alineado con los campos en escritorio.
- «Cerrar» se alinea al extremo derecho del grupo de acciones del detalle. Las
  acciones de modificar y anular permanecen juntas a la izquierda.
- Las nuevas emisiones automáticas usan referencias cortas `CP-000001`,
  `CP-000002`, etc. El número corresponde al compromiso, no al número de cuota
  ni al estudiante. La matrícula y el historial de auditoría siguen vinculados
  al registro original.
- El backend mantiene un contador global persistente con bloqueo en base de
  datos. No se reinicia por año ni por anulación; los códigos ya emitidos se
  conservan. Si existen referencias numéricas anteriores o integraciones que
  reservan una, la generación continúa después de ellas.
- La migración nueva es V10; no se modifican migraciones anteriores ni se
  renumeran compromisos existentes. El frontend no calcula ni asigna códigos.

Verificación de estos ajustes:

- Frontend: 18 pruebas de Finanzas aprobadas, compilación de producción correcta
  y revisión visual de filtros y detalle en escritorio y pantalla pequeña.
- Backend: 495 casos verificados sin fallos ni errores en los reportes finales,
  incluida emisión simultánea desde dos matrículas distintas y ejecución de V10
  en H2 con modo MySQL. La primera ejecución detectó una incidencia de limpieza
  de carpeta temporal en una prueba de adjuntos; esa clase se repitió y sus diez
  casos pasaron, conservando la cobertura de la ejecución completa.
- Empaquetado y umbral de cobertura correctos: 93,43 % de instrucciones y
  94,01 % de líneas. No se cambiaron umbrales ni aserciones para superar la
  comprobación.
- Backend QA local reconstruido y reiniciado sobre la misma base H2 persistente,
  con respaldo previo. Se compararon los dos compromisos existentes antes y
  después: mantuvieron IDs, códigos, importes, saldos, estados y versiones.
- Se emitió desde la interfaz local «Prueba de correlativo 07NOV», que recibió
  `CP-000001`. Sus capturas están en `outputs/us12-correlativo-y-alineacion` en
  la carpeta de trabajo exterior al repositorio. No se actualizó el servidor
  desplegado ni se publicaron cambios en GitHub.
