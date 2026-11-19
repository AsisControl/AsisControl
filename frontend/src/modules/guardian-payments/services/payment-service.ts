import { api, apiBlob } from "@/core/api/client";
import { saveBlob } from "@/shared/lib/save-blob";
import type { CardScenario, FamilyStudent, ObligationDetail, OrderReceipt, PaymentMethod, PaymentOrder, ReceiptHistoryItem } from "../types";

export const OBLIGATIONS_PATH = "/api/apoderado/obligaciones";
export const ORDERS_PATH = "/api/pagos/ordenes";
export const RECEIPTS_PATH = "/api/apoderado/comprobantes";
export const getIndividualReceipt = (id: number) => api<ReceiptHistoryItem>(`${RECEIPTS_PATH}/${id}`);
export async function downloadIndividualReceipt(id: number, code: string) {
  const blob = await apiBlob(`${RECEIPTS_PATH}/${id}/descargar`);
  saveBlob(blob, `comprobante-${code.replace(/[^a-zA-Z0-9_-]/g, "")}.html`);
}
export const getStudents = () => api<FamilyStudent[]>(`${OBLIGATIONS_PATH}/estudiantes`);
export const getObligation = (id: number) => api<ObligationDetail>(`${OBLIGATIONS_PATH}/${id}`);
export const startOrder = (obligacionesIds: number[], metodo: PaymentMethod, key: string) =>
  api<PaymentOrder>("/api/pagos/iniciar", { method: "POST", headers: { "Idempotency-Key": key }, body: JSON.stringify({ obligacionesIds, metodo }) });
export const getOrder = (id: number) => api<PaymentOrder>(`${ORDERS_PATH}/${id}`);
export const confirmCard = (id: number, escenario: CardScenario) =>
  api<PaymentOrder>(`${ORDERS_PATH}/${id}/confirmar-tarjeta`, { method: "POST", body: JSON.stringify({ escenario }) });
export const authenticateCard = (id: number, aprobar: boolean) =>
  api<PaymentOrder>(`${ORDERS_PATH}/${id}/autenticar`, { method: "POST", body: JSON.stringify({ aprobar }) });
export const getReceipt = (id: number) => api<OrderReceipt>(`${ORDERS_PATH}/${id}/comprobante`);
export async function downloadReceipt(id: number, code: string) {
  const blob = await apiBlob(`${ORDERS_PATH}/${id}/comprobante/descargar`);
  saveBlob(blob, `comprobante-${code.replace(/[^a-zA-Z0-9_-]/g, "")}.html`);
}
/** El celular usa únicamente la capacidad del QR; no envía sesión, cookies ni identidad. */
export const getQrOrder = (token: string) => api<PaymentOrder>(`/api/pagos/qr/${encodeURIComponent(token)}`,
  { credentials: "omit", referrerPolicy: "no-referrer" }, false);
export const confirmQr = (token: string, accion: "APROBAR" | "RECHAZAR") =>
  api<PaymentOrder>(`/api/pagos/qr/${encodeURIComponent(token)}/confirmar`, {
    method: "POST", credentials: "omit", referrerPolicy: "no-referrer", body: JSON.stringify({ accion }),
  }, false);
