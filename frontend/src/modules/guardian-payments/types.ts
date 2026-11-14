import type { PaymentCommitment } from "@/modules/finance";

export type FamilyStudent = { id: number; codigoAlumno: string; nombreCompleto: string };
export type PaymentMethod = "TARJETA" | "QR";
export type OrderStatus = "PENDIENTE" | "REQUIERE_AUTENTICACION" | "COMPLETADO" | "RECHAZADO" | "EXPIRADO";
export type CardScenario = "APROBADO" | "FONDOS_INSUFICIENTES" | "RECHAZADO" | "REQUIERE_AUTENTICACION";
export type PaymentOrder = {
  id: number; codigo: string; metodo: PaymentMethod; estado: OrderStatus; moneda: "PEN";
  montoBase: number; mora: number; montoTotal: number; creadaEn: string; expiraEn: string;
  pagadaEn: string | null; institucion: string; apoderado: string;
  items: Array<{ idCompromiso: number; codigoCompromiso: string; alumno: string; concepto: string; numeroCuota: number | null; monto: number }>;
  qrUrl: string | null; paymentIntentId: string | null; clientSecret: string | null;
  mensaje: string | null; simulador: boolean;
};
export type PaymentTransaction = {
  id: number; idCompromisoPago: number; numeroOperacion: string; monto: number;
  medioPago: string; estado: string; fechaTransaccion: string; observacion: string | null;
};
export type PaymentReceipt = {
  id: number; idTransaccionPago: number; tipoComprobante: string; serie: string; numero: string;
  fechaEmision: string; montoTotal: number; idArchivoAdjunto: number | null; fechaCreacion?: string;
};
export type ObligationDetail = { compromiso: PaymentCommitment; transacciones: PaymentTransaction[]; comprobantes: PaymentReceipt[] };
export type OrderReceipt = { orden: PaymentOrder; comprobantes: PaymentReceipt[] };
export type ReceiptHistoryItem = {
  id: number; codigo: string; fechaEmision: string; montoTotal: number; moneda: "PEN";
  alumno: string; concepto: string; numeroCuota: number | null; codigoCompromiso: string;
  medioPago: string; idOrden: number | null; codigoOrden: string | null;
  institucion: string; apoderado: string;
};
