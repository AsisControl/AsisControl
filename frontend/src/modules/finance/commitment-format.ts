import type { FinancialStatus, PaymentCommitment, TransactionStatus } from "./types";

const DATE = new Intl.DateTimeFormat("es-PE", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  timeZone: "UTC",
});

export function moneyLabel(amount: number, currency = "PEN") {
  if (!/^[A-Z]{3}$/.test(currency)) {
    return `${amount.toLocaleString("es-PE", { minimumFractionDigits: 2 })} ${currency}`;
  }
  return new Intl.NumberFormat("es-PE", {
    style: "currency",
    currency,
  }).format(amount);
}

export const quotaLabel = (quota: number | null) =>
  quota === null ? "Sin cuota registrada" : `Cuota ${quota}`;

/** Los vencimientos son fechas de calendario, sin cambio de día por zona horaria. */
export const dueDateLabel = (date: string) => DATE.format(new Date(`${date}T00:00:00Z`));

export const financialLabels: Record<FinancialStatus, string> = {
  PENDIENTE: "Pendiente",
  PARCIAL: "Pago parcial",
  PAGADO: "Pagado",
  VENCIDO: "Vencido",
  ANULADO: "Anulado",
  EXONERADO: "Exonerado",
};

export const financialStatus = (record: PaymentCommitment): FinancialStatus =>
  record.estadoAdministrativo === "ANULADO" ? "ANULADO" : record.estadoFinanciero;

export const financialLabel = (record: PaymentCommitment) => financialLabels[financialStatus(record)];

export function financialTone(record: PaymentCommitment): "success" | "warning" | "danger" | "info" | "neutral" {
  return financialToneForStatus(financialStatus(record));
}

export function financialToneForStatus(state: FinancialStatus): "success" | "warning" | "danger" | "info" | "neutral" {
  switch (state) {
    case "PAGADO": return "success";
    case "VENCIDO": return "danger";
    case "PARCIAL": return "warning";
    case "PENDIENTE": return "info";
    case "EXONERADO": return "info";
    default: return "neutral";
  }
}

export const transactionLabels: Record<TransactionStatus, string> = {
  APROBADA: "Aprobada",
  PENDIENTE: "Pendiente",
  RECHAZADA: "Rechazada",
  EXPIRADA: "Expirada",
  ANULADA: "Anulada",
};

export function transactionTone(state: TransactionStatus): "success" | "warning" | "danger" | "info" | "neutral" {
  switch (state) {
    case "APROBADA": return "success";
    case "PENDIENTE": return "warning";
    case "RECHAZADA": return "danger";
    default: return "neutral";
  }
}

const PAYMENT_METHODS: Record<string, string> = {
  EFECTIVO: "Efectivo",
  TRANSFERENCIA_BANCARIA: "Transferencia bancaria",
  TARJETA_DEBITO: "Tarjeta de débito",
  TARJETA_CREDITO: "Tarjeta de crédito",
  YAPE: "Yape",
  PLIN: "Plin",
  POS: "POS",
  TARJETA_SIMULADA: "Tarjeta (pago en línea)",
  QR_SIMULADO: "QR (pago en línea)",
};

export const paymentMethodLabel = (method: string) =>
  PAYMENT_METHODS[method] ?? method.charAt(0) + method.slice(1).toLowerCase().replaceAll("_", " ");

const MOMENT = new Intl.DateTimeFormat("es-PE", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  timeZone: "America/Lima",
});

/** Fecha y hora de una transacción en la hora del colegio. */
export const momentLabel = (instant: string) => MOMENT.format(new Date(instant));

export const gradeSectionLabel = (record: PaymentCommitment) =>
  record.grado == null ? null : `${record.grado}.° grado${record.seccion ? ` · Sección ${record.seccion}` : ""}`;
