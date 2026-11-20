# US-12, US-13 y US-48: importes, estados e historial de pagos

Refinamiento solicitado el 8 de noviembre de 2026. Complementa
[la guía de integración](US13_US48_IMPLEMENTACION.md).
La mejora posterior renombra Operaciones a **Seguimiento de pagos** y controla
[las solicitudes pendientes duplicadas](US13_CONTROL_PAGOS_EN_CURSO.md).

## Lo que cambia en la presentación

| Antes | Ahora |
|---|---|
| Monto y saldo en cada fila; una cuota pagada parecía valer cero en la vista del apoderado. | Una columna **Monto** muestra el importe original del compromiso. **Pagado** indica que fue cancelado. |
| «Activo» junto a «Pagado». | Un solo estado visible: **Pendiente, Vencido, Pago parcial, Pagado o Anulado**. |
| Dos filtros de estado diferentes en Finanzas. | Un filtro por el estado que ve el usuario, incluida la anulación. |
| Historial que consultaba solo órdenes nuevas. | **Historial de pagos** consulta todos los comprobantes autorizados, también los anteriores sin orden. |
| Órdenes aprobadas, rechazadas y pendientes mezcladas con comprobantes. | **Operaciones** conserva esos intentos y permite retomar los pendientes. |
| Referencias largas y aleatorias de las órdenes. | Referencias **OP-000001** para órdenes y **REC-000001** para comprobantes. |
| Avisos repetitivos de pruebas y descarga básica. | Textos de uso cotidiano, constancias con encabezado, datos del pago, detalle de cuotas, total y formato de impresión. |

El saldo **sigue siendo necesario dentro del sistema**: permite calcular lo que
queda por pagar, respetar abonos anteriores e impedir cobros duplicados. No se
eliminó de la base ni se sustituyó el importe original por cero cuando se paga.
Si existe un abono parcial, el detalle muestra el importe abonado y el pendiente.
La selección para pagar siempre suma únicamente el importe pendiente real.

«Activo» se conserva internamente para diferenciar un compromiso vigente de uno
anulado. No significa que tenga una deuda pendiente. La interfaz muestra el
estado de pago efectivo, con prioridad de la anulación, sin cambiar contratos que
otros módulos ya usan ni permitir editar compromisos con pagos registrados.

## Diagnóstico del historial local

Se revisaron los registros reales antes de actualizar el backend:

| Familia ficticia | Compromisos pagados | Transacciones aprobadas | Comprobantes existentes | Órdenes aprobadas / rechazadas |
|---|---:|---:|---:|---:|
| demo.pagos.1 | 6 | 6 | 6 | 3 / 1 |
| demo.pagos.2 | 3 | 3 | 3 | 2 / 1 |

No faltaban comprobantes en esos casos. El historial anterior excluía los pagos
anteriores sin orden: CP-000002, CP-000005 y CP-000008. Además, una sola orden
agrupaba dos cuotas. La última cuota CP-000007 ya estaba pagada por el usuario y
se conserva así.

Estas cantidades no tienen por qué ser iguales en todos los casos: un compromiso
pendiente todavía no tiene comprobante; varios abonos a una cuota pueden producir
varios comprobantes; una operación puede pagar varias cuotas y un intento
rechazado no produce comprobante. El historial nuevo parte de los comprobantes
reales, sin fabricar pagos para hacer coincidir números.

## Cambios del backend y de la base para el equipo

- Se añaden `GET /api/apoderado/comprobantes`, `GET /api/apoderado/comprobantes/{id}`
  y `GET /api/apoderado/comprobantes/{id}/descargar`. La consulta paginada, el detalle
  y la descarga exigen sesión, permiso y asociación vigente con el estudiante.
- Se conservan las rutas de órdenes y la descarga consolidada de una operación.
  Un comprobante anterior sin orden se consulta y descarga por su propio ID.
- **V12** añade `OrdenPago.qr_referencia`, conserva en ella la referencia firmada
  anterior y convierte el código visible a `OP-` seguido del ID con al menos seis
  dígitos. El identificador seguro del QR continúa siendo independiente del folio
  visible; conocer OP-000001 no permite confirmar ese pago.
- El contador usa la identidad de la base y resiste emisiones concurrentes. No es
  una numeración fiscal ni promete ausencia de saltos por operaciones revertidas.
  No se reinicia ni reutiliza al cambiar de estudiante o año.
- `REC-` identifica de forma estable al comprobante por su ID. Las referencias
  históricas guardadas se conservan para auditoría; no se borran pagos anteriores.
- El HTML descargable es autónomo e imprimible en A4, escapa los textos y no carga
  recursos externos. Incluye institución configurada, referencia, fecha de Perú,
  medio, estudiante, concepto, cuota, importe y total. No inventa RUC ni datos
  fiscales. También puede imprimirse como PDF desde el navegador.

Integrar V12 **después de V10 y V11**, sin editar migraciones ya aplicadas. En MySQL
del equipo debe aplicarse V12 con Flyway. Respaldar antes y comprobarla en una copia
de esa base.

En esta base H2 persistente local, con Flyway desactivado, `ddl-auto=update` no
añadió automáticamente la columna nueva. Se detuvo el backend, se verificó un
respaldo y se aplicó explícitamente V12 antes de reiniciar. Las siete órdenes
existentes conservaron sus referencias QR y pagos. Reiniciar solamente el JAR sin
actualizar esa estructura no basta para este entorno anterior.

La presentación refinada no cambia la pasarela: continúa siendo una simulación
local y no se cobran fondos bancarios. Se conserva esa distinción en configuración,
documentación y auditoría. Las constancias siguen siendo documentos de control
institucional; no son comprobantes electrónicos de SUNAT. La mora sigue en cero.

## Verificación de este refinamiento

Resultados del 8 de noviembre de 2026:

- **Backend:** suite completa de 533 casos aprobada, seguida de regresiones finales
  por moneda histórica y numeración. Los reportes finales reúnen **535 pruebas en
  81 clases, sin fallos, errores ni omisiones**. Empaquetado correcto. Cobertura:
  instrucciones 93.70 %, líneas 94.23 %, ramas 77.46 %; umbrales conservados.
- **Frontend:** **662 pruebas aprobadas en 53 archivos**. Cobertura: instrucciones
  95.19 %, líneas 96.85 %, ramas 90.21 %, funciones 94.06 %. También se comprobaron
  nombres de medios de pago antiguos y se recompiló tras el ajuste visual final.
  Revisión de estilo sin errores, con una advertencia previa no relacionada en
  `schedules/tests/timetable.test.jsx:580`. Compilación final correcta.
- **API local actualizada:** 15 comprobaciones aprobadas sobre la base persistente:
  los seis comprobantes de la familia 1 y los tres de la familia 2, incluidos los
  tres anteriores sin orden; importes originales, referencias cortas, detalle y
  descarga privada. Otra familia recibe 403 al consultar o descargar un documento
  ajeno. Esta comprobación no creó ni confirmó pagos nuevos.
- **Datos y migración local:** respaldo verificado antes de actualizar; V12 aplicada
  explícitamente en H2. Las siete órdenes conservaron referencias QR y pagos. Los
  tres compromisos anteriores a US-13 conservaron íntegros sus datos económicos.
- **Revisión visual:** obligaciones con monto original y estado Pagado; historial
  con los seis comprobantes, detalle de REC-000001 anterior sin orden, operaciones
  con OP cortos y HTML descargado de REC-000009 con el formato profesional.

Las capturas, una constancia anterior y una reciente descargadas, y el resumen de
las comprobaciones están en `outputs/refinamiento-pagos`, fuera del repositorio.
La aplicación actualizada está en **http://localhost:3001/pagos**. No hace falta
reiniciar otra vez para ver este cambio; recargar la página basta. Se conservan
los accesos del archivo privado `qa-us12-evidencias/ACCESO_PAGOS_LOCAL.txt`.

No se ejecutó V12 en una instancia real de MySQL/RDS ni se desplegó esta actualización
en el servidor compartido. La publicación se realiza mediante una rama de trabajo y
un Pull Request hacia develop, incluyendo código y migraciones en la misma entrega.
