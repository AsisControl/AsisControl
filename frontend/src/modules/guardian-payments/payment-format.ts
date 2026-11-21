import { quotaLabel, type PaymentCommitment } from "@/modules/finance";
import type { OrderItem, OrderStatus } from "./types";
export { moneyLabel, dueDateLabel, quotaLabel, financialLabel, financialLabels, financialStatus, financialTone } from "@/modules/finance";
export const orderLabels: Record<OrderStatus, string> = {
  PENDIENTE: "Esperando confirmación", REQUIERE_AUTENTICACION: "Autenticación pendiente",
  COMPLETADO: "Pago aprobado", RECHAZADO: "Pago rechazado", EXPIRADO: "Expirado",
};
export const isPending = (status: OrderStatus) => status === "PENDIENTE" || status === "REQUIERE_AUTENTICACION";
export const canPay = (item: PaymentCommitment) => item.estadoAdministrativo === "ACTIVO" && item.estadoFinanciero !== "EXONERADO" && item.saldo > 0 && item.moneda === "PEN";

const PAYMENT_MONTH = new Intl.DateTimeFormat("es-PE", { month: "long", timeZone: "UTC" });

export function paymentLine({ concepto, numeroCuota, codigoCompromiso, mes, anioAcademico, referenciaCobro, tipo }: Omit<OrderItem, "idCompromiso" | "alumno" | "monto">) {
  const period = mes != null && mes >= 1 && mes <= 12 && anioAcademico
    ? `${PAYMENT_MONTH.format(new Date(Date.UTC(anioAcademico, mes - 1, 1)))} ${anioAcademico}`
    : tipo !== "OTRO" && numeroCuota != null ? quotaLabel(numeroCuota) : null;
  return [concepto, period, referenciaCobro, codigoCompromiso].filter(Boolean).join(" · ");
}

export function countdownLabel(seconds: number) {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
}
export function walletUrl(value: string | null): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    return ["http:", "https:"].includes(url.protocol) && url.pathname.startsWith("/pagar-qr/") ? url.href : null;
  } catch { return null; }
}

export const receiptCode = (id: number) => `REC-${String(id).padStart(6, "0")}`;
export function paymentMethodLabel(method: string) {
  const labels: Record<string, string> = { TARJETA: "Tarjeta", TARJETA_CREDITO: "Tarjeta de crédito", TARJETA_DEBITO: "Tarjeta de débito", POS: "Tarjeta (POS)", TARJETA_SIMULADA: "Tarjeta", QR: "Código QR", QR_SIMULADO: "Código QR", EFECTIVO: "Efectivo", YAPE: "Yape", PLIN: "Plin", TRANSFERENCIA: "Transferencia bancaria", TRANSFERENCIA_BANCARIA: "Transferencia bancaria", DEPOSITO: "Depósito bancario" };
  return labels[method] ?? "Otro medio de pago";
}
