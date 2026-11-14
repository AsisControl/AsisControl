# Seguimiento de pagos y prevención de solicitudes duplicadas

Actualización solicitada el 8 de noviembre de 2026 para US-13 / US-48.

## Organización de la pantalla

- **Obligaciones:** cuotas y compromisos del estudiante.
- **Seguimiento de pagos:** solicitudes iniciadas, su resultado y la posibilidad de
  continuar una solicitud vigente.
- **Historial de pagos:** pagos aprobados con todos sus comprobantes, incluidos los
  anteriores que no tienen una orden asociada.

Una solicitud aprobada mantiene **Ver comprobante** en Seguimiento de pagos. Es
un acceso al documento de la misma operación, no una nueva emisión. Si la operación
pagó varias cuotas, muestra su constancia conjunta. El historial permite consultar
también cada comprobante individual.

## Regla para un pago pendiente

Antes de este ajuste podían existir varias solicitudes pendientes de una cuota.
La confirmación bloqueaba el compromiso y revalidaba el saldo para impedir aplicarlo
dos veces. Se mantiene esa defensa y se añade un control desde el inicio.

| Situación | Comportamiento |
|---|---|
| Reintento de la misma solicitud por doble clic o respuesta perdida, con la misma clave | Devuelve la misma orden; no genera otra transacción. |
| Nueva solicitud que incluye una cuota con un pago pendiente vigente | Se rechaza la solicitud completa; se indica que debe continuarse el pago en curso. |
| Se intenta cambiar de tarjeta a QR creando otra solicitud para esa cuota | Se conserva el pago original y su medio; no se crea una segunda solicitud paralela. |
| Selección de varias cuotas y solo una tiene un pago en curso | No se inicia ninguna parte del lote. Se debe continuar el pago pendiente o revisar la selección. |
| Otro apoderado asociado intenta pagar esa misma cuota simultáneamente | Se aplica el mismo bloqueo. El mensaje no revela su identidad, su orden ni su QR. |
| La solicitud sigue pendiente o espera autenticación adicional | Puede continuarse desde Seguimiento de pagos con el tiempo que le quedaba; abrirla no reinicia los cinco minutos. |
| La solicitud vence o resulta rechazada | No disminuye el importe pendiente; permite un nuevo intento con una clave nueva. |
| La cuota ya fue pagada | No se admite un nuevo pago del mismo importe. Repetir la confirmación de la orden aprobada devuelve su resultado sin duplicar comprobantes. |

La pantalla ofrece **Ir a Seguimiento de pagos** cuando recibe el conflicto. Si
el pago lo está realizando otro apoderado, esa orden no aparece en el seguimiento
personal: debe esperarse su resultado o vencimiento antes de volver a intentarlo.
La consulta de obligaciones y de comprobantes continúa sujeta a la asociación con
el estudiante.

## Cambios del backend para el equipo

- `POST /api/pagos/iniciar` conserva la cabecera `Idempotency-Key` y su contrato.
- Una nueva solicitud que se solapa con un pago vigente devuelve **HTTP 409** con
  código `PAGO_EN_CURSO`; no inserta orden, detalles ni transacciones de esa solicitud.
- El control comprende `PENDIENTE` y `REQUIERE_AUTENTICACION` con vencimiento futuro,
  independientemente del apoderado que inició el pago. Las solicitudes vencidas no
  bloquean un nuevo intento aunque su estado almacenado aún espere actualización.
- La comprobación se hace bajo bloqueos de los compromisos, en orden de ID, con
  lectura `READ_COMMITTED` en el inicio. Así una solicitud que esperó a otra puede
  ver sus cambios confirmados. No se bloquea después la orden competidora y se
  evita invertir el orden de bloqueo de la confirmación.
- Se conserva la comprobación de saldo y permisos al confirmar, incluyendo pagos
  manuales o cambios institucionales realizados entre el inicio y la confirmación.
- No se cambian las tablas ni se añade una migración para esta regla. Para integrar
  la versión completa siguen siendo necesarias las migraciones anteriores V10–V12.

La elección de lectura actualizada se apoya en la
[documentación de aislamiento de MySQL](https://dev.mysql.com/doc/refman/8.4/en/innodb-transaction-isolation-levels.html):
`READ_COMMITTED` obtiene una instantánea nueva por consulta. Los bloqueos del
compromiso son los que serializan la creación para una misma cuota.

## Verificación

Comprobaciones terminadas el 8 de noviembre de 2026:

- **Backend:** 33 casos de flujo, seguridad, migración y concurrencia aprobados,
  más los cinco casos nuevos de `PagoPendienteHttpTest`. Los seis casos de
  concurrencia incluyen claves distintas, dos apoderados, confirmar mientras se
  inicia otra solicitud y la defensa frente a órdenes duplicadas anteriores.
  Los reportes finales acumulados contienen **543 pruebas en 82 clases, sin
  fallos, errores ni omisiones**; no corresponden a una sola ejecución de todos
  los módulos. El empaquetado y el control de cobertura aprobaron: instrucciones
  93.69 %, líneas 94.24 % y ramas 77.44 %, conservando los umbrales.
- **Frontend:** **665 pruebas aprobadas en 54 archivos**. Se comprobó el conflicto
  de pago en curso, el acceso a Seguimiento, la continuación del QR original sin
  otro POST de inicio, el doble clic y la distinción frente a un error temporal.
  Cobertura: instrucciones 95.18 %, líneas 96.85 %, ramas 90.22 %, funciones
  94.06 %. Compilación correcta; lint sin errores y con una advertencia previa
  en `schedules/tests/timetable.test.jsx:580`.
- **Base local:** después del reinicio se repitieron las 15 comprobaciones de
  historial, importes, descargas y aislamiento familiar. Permanecen los seis
  comprobantes de la primera familia y los tres de la segunda. Los tres
  compromisos anteriores a US-13 conservaron sus datos. No se crearon ni
  confirmaron pagos nuevos para esta comprobación local.
- **Revisión visual:** Seguimiento de pagos muestra el nombre nuevo, las órdenes
  existentes y sus accesos a comprobantes, con la explicación de la vigencia. La
  captura está en `outputs/seguimiento-pagos/seguimiento-actualizado.png`, fuera
  del repositorio.

Frontend y backend locales quedaron activos en **http://localhost:3001/pagos** y
puerto 8081, con los mismos accesos privados. Recargar la página permite ver el
cambio. No se restablecieron ni se borraron los pagos anteriores.

Las pruebas de concurrencia usan transacciones independientes con H2; no se
ejecutaron contra MySQL/RDS real. La entrega se publica mediante un Pull Request
hacia develop; no se desplegó en el servidor compartido.
