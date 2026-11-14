# Contrato de integración US-13 / US-48

Contrato de implementación sobre el modelo existente: CompromisoPago, Matricula,
AlumnoApoderado, TransaccionPago y Comprobante. Moneda PEN. No duplica personas,
matrículas ni obligaciones. Pasarela SIMULADA, sin dinero real ni comprobantes SUNAT.
Mora temporal: 0.00 mientras no exista política aprobada.

## API autenticada (APODERADO y PAGOS_LEER)

- `GET /api/apoderado/obligaciones/estudiantes`: array `{id, codigoAlumno, nombreCompleto}`.
- `GET /api/apoderado/obligaciones?page=0&size=10&idAlumno=&estado=`:
  página Spring normalizable, content del tipo `PaymentCommitment` existente en
  frontend/modules/finance/types.ts. Estados PENDIENTE, VENCIDO, PARCIAL, PAGADO,
  ANULADO o todos. Siempre limitado a asociaciones vigentes del usuario actual.
- `GET /api/apoderado/obligaciones/{id}`:
  `{compromiso: PaymentCommitment, transacciones: Transaccion[], comprobantes: Comprobante[]}`.
  Tipos Transaccion/Comprobante compatibles con PagoDto existente.
- `GET /api/apoderado/comprobantes?page=0&size=10`: página de comprobantes reales
  de pagos aprobados, incluidos los anteriores sin orden, restringidos a estudiantes
  asociados. Detalle por `/{id}` y descarga HTML por `/{id}/descargar` bajo el mismo
  control de autorización. Campos: `id`, `codigo` (REC-000001), `fechaEmision`,
  `montoTotal`, `moneda`, `alumno`, `concepto`, `numeroCuota`, `codigoCompromiso`,
  `medioPago`, `idOrden` y `codigoOrden` opcionales, `institucion`, `apoderado`.
- `POST /api/pagos/iniciar`, header `Idempotency-Key` UUID (obligatorio):
  body `{obligacionesIds: number[], metodo: "TARJETA"|"QR"}`. Máximo 20 IDs distintos,
  importe calculado en servidor sobre saldo restante; pagos parciales previos sí
  pueden terminar de cancelarse, sin introducir un nuevo monto parcial manual.
  Devuelve `OrdenPago`. Todos los IDs deben ser propios; no se acepta un apoderadoId
  ni un monto del cliente. Reintentos con la misma clave y selección devuelven la
  misma orden; reutilizarla con otros datos genera conflicto.
  Una nueva clave que incluya una cuota en otra orden pendiente vigente devuelve
  409 `PAGO_EN_CURSO`, sin crear registros. Incluye autenticaciones pendientes y
  solicitudes de cualquier apoderado asociado; una orden vencida no reserva la cuota.
- `GET /api/pagos/ordenes?page=0&size=10`: página de `OrdenPago`, propias.
  El folio visible es OP-000001 (ID con un mínimo de seis dígitos). La referencia
  firmada del QR es independiente e inmutable; V12 preserva las URLs anteriores.
- `GET /api/pagos/ordenes/{id}`: `OrdenPago` propia; evalúa expiración.
- `POST /api/pagos/ordenes/{id}/confirmar-tarjeta`:
  `{escenario: "APROBADO"|"FONDOS_INSUFICIENTES"|"RECHAZADO"|"REQUIERE_AUTENTICACION"}`.
  Devuelve OrdenPago. Solo simulador, sin PAN/CVV ni claves reales.
- `POST /api/pagos/ordenes/{id}/autenticar`:
  `{aprobar: boolean}`. Completa o rechaza el desafío 3DS simulado.
- `GET /api/pagos/ordenes/{id}/comprobante`: `ReciboOrden`, solo completadas.
- `GET /api/pagos/ordenes/{id}/comprobante/descargar`: HTML escapado como adjunto,
  `Content-Disposition`, solo órdenes propias completadas. Comprobante interno.

## API QR de capacidad temporal (sin sesión de apoderado en el celular)

- `GET /api/pagos/qr/{token}`: OrdenPago con los datos mínimos del pago para la
  billetera simulada. No devuelve IDs de personas, documentos, contactos ni otros pagos.
- `POST /api/pagos/qr/{token}/confirmar`: `{accion:"APROBAR"|"RECHAZAR"}` -> OrdenPago.
- Token firmado HMAC y acotado a una sola orden QR; vigencia 5 min. Sin endpoint
  público que pueda aprobar por ID de orden ni callback bancario sin autenticación.
- URL `{FRONTEND_URL}/pagar-qr/{token}`. Token no va en auditoría ni se persiste
  como secreto en claro. Puede regenerarse con clave estable del servidor para
  responder un reintento idempotente sin invalidar el QR de la otra pantalla.
- El estado PC se consulta por ID autenticado cada 2 s; el QR público no muestra
  el comprobante ni más historial. Detener polling y temporizador al cerrar/terminar.

## Tipos compartidos (JSON)

`OrdenPago`:

```ts
{
  id: number;
  codigo: string;
  metodo: "TARJETA" | "QR";
  estado: "PENDIENTE" | "REQUIERE_AUTENTICACION" | "COMPLETADO" | "RECHAZADO" | "EXPIRADO";
  moneda: "PEN";
  montoBase: number;
  mora: number;
  montoTotal: number;
  creadaEn: string;
  expiraEn: string;
  pagadaEn: string | null;
  institucion: string;
  apoderado: string; // nombre de presentación, no documento
  items: Array<{
    idCompromiso: number;
    codigoCompromiso: string;
    alumno: string;
    concepto: string;
    numeroCuota: number | null;
    monto: number;
  }>;
  qrUrl: string | null;
  paymentIntentId: string | null; // pi_sim_... identifica intento local
  clientSecret: string | null; // solo mock, nunca clave Stripe real
  mensaje: string | null;
  simulador: true;
}
```

`ReciboOrden`:

```ts
{
  orden: OrdenPago;
  comprobantes: Array<{
    id: number;
    idTransaccionPago: number;
    tipoComprobante: string;
    serie: string;
    numero: string;
    fechaEmision: string;
    montoTotal: number;
    idArchivoAdjunto: number | null;
  }>;
}
```

## Invariantes

- Al iniciar, crear transacciones PENDIENTE ligadas a la orden y a cada compromiso.
- Confirmación atómica: bloquear orden y compromisos en orden creciente de ID,
  revalidar asociación, estado administrativo y saldo contra la instantánea;
  aprobar transacciones, actualizar montoPagado/estado del compromiso y emitir
  comprobante interno por transacción con una vista consolidada de orden.
- Repetir confirmación devuelve resultado anterior sin duplicar pagos/comprobantes.
- Si otra orden cambió el saldo, rechazar esta sin aplicar pagos parciales al lote.
- Rechazos y expiraciones no reducen saldos y son visibles como intentos fallidos.
- Cerrar el acceso antiguo que permitía a APODERADO registrar directamente una
  transacción APROBADA por `/api/transacciones-pago`; la vía manual institucional
  permanece autorizada para roles institucionales, no para apoderados.
- La UI `/pagar-qr/{token}` debe ser pública y vivir fuera del layout con login.
- Las semillas de demostración son opt-in locales; nunca crean credenciales demo
  automáticamente en producción. Dos apoderados con estudiantes distintos.
