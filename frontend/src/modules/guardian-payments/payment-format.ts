import type { PaymentCommitment } from "@/modules/finance";
import type { OrderStatus } from "./types";
export { moneyLabel, dueDateLabel, quotaLabel, financialLabel, financialLabels, financialStatus, financialTone } from "@/modules/finance";
export const orderLabels: Record<OrderStatus, string> = {
  PENDIENTE: "Esperando confirmación", REQUIERE_AUTENTICACION: "Autenticación pendiente",
  COMPLETADO: "Pago aprobado", RECHAZADO: "Pago rechazado", EXPIRADO: "Orden vencida",
};
export const isPending = (status: OrderStatus) => status === "PENDIENTE" || status === "REQUIERE_AUTENTICACION";
export const canPay = (item: PaymentCommitment) => item.estadoAdministrativo === "ACTIVO" && item.estadoFinanciero !== "EXONERADO" && item.saldo > 0 && item.moneda === "PEN";
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
