import type { FinancialStatus, PaymentCommitment } from "./types";

const DATE = new Intl.DateTimeFormat("es-PE", {
  day: "2-digit",
  month: "short",
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
};

export const financialStatus = (record: PaymentCommitment): FinancialStatus =>
  record.estadoAdministrativo === "ANULADO" ? "ANULADO" : record.estadoFinanciero;

export const financialLabel = (record: PaymentCommitment) => financialLabels[financialStatus(record)];

export function financialTone(record: PaymentCommitment): "success" | "warning" | "danger" | "info" | "neutral" {
  switch (financialStatus(record)) {
    case "PAGADO": return "success";
    case "VENCIDO": return "danger";
    case "PARCIAL": return "warning";
    case "PENDIENTE": return "info";
    default: return "neutral";
  }
}
