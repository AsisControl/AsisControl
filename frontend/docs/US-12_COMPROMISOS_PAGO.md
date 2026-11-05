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
  integración local que ya contiene las correcciones de la ficha. No se mezclaron
  nuevas revisiones remotas ni se publicó la rama durante esta implementación.

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

La migración `V6__compromisos_pago_administrativos.sql` conserva los importes y
monedas históricos, recupera la consulta de los compromisos que se anularon con
el mecanismo anterior y no inventa números de cuota. Un compromiso anterior sin
cuota registrada bloquea una nueva emisión coincidente del mismo estudiante,
año y concepto hasta su revisión. Los registros históricos en otra moneda se
presentan en su moneda original y no permiten modificar el importe o la fecha;
pueden anularse con motivo si no tienen pagos aprobados. Las emisiones nuevas
son exclusivamente en PEN.

En el perfil local con H2 en memoria, reiniciar el backend borra los datos de
prueba. En una base persistente, respaldar los datos y aplicar el despliegue
habitual del proyecto. El servidor de desarrollo del frontend detecta los
cambios de la interfaz; una instalación con archivos compilados necesita una
nueva compilación.

El paquete del backend ya fue generado durante esta verificación. Desde la
carpeta `backend`, en la terminal que conserva tu configuración local, puedes
iniciar la versión actualizada con:

```powershell
java -jar .\controller\target\asiscontrol-controller-1.0.0-SNAPSHOT.jar
```

Si el backend anterior sigue abierto, detenerlo antes de iniciar este. Desde
`frontend`, iniciar la interfaz con `npm.cmd run dev` y abrir **Finanzas**.
Si no aparecen matrículas al emitir, comprobar primero las precondiciones del
año académico y de la matrícula; no se emiten compromisos directamente desde
una ficha sin matrícula.

## Verificación realizada · 4 de noviembre de 2026

| Comprobación | Resultado |
|---|---|
| Backend, `mvn verify` | 259 pruebas aprobadas; sin fallos, errores ni omitidas. |
| Cobertura backend | 93,23 % de líneas; supera el umbral del proyecto del 90 %. |
| Migración V6 final | SQL exacto ejecutado en H2 con modo MySQL; incluye registros antiguos, moneda original, anulación previa, espacios y tabulaciones. Paquete actualizado generado después de la comprobación. |
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
