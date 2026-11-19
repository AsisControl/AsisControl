import { api } from "@/core/api/client";
import type { BenefitPreview, ChargeConcept, OptionalChargeOffer, PensionPlan, PensionRate, StudentBenefit } from "../types";

export const SETTINGS_PATH = "/api/configuracion-pagos";
export const getConcepts = () => api<ChargeConcept[]>(`${SETTINGS_PATH}/conceptos`);
export const getYears = () => api<number[]>(`${SETTINGS_PATH}/anios`);
export const getRates = () => api<PensionRate[]>(`${SETTINGS_PATH}/tarifas`);
export const getBenefits = () => api<StudentBenefit[]>(`${SETTINGS_PATH}/beneficios`);
export function saveSettings<T>(resource: string, body: unknown, id?: number) {
  return api<T>(`${SETTINGS_PATH}/${resource}${id == null ? "" : `/${id}`}`, {
    method: id == null ? "POST" : "PUT", body: JSON.stringify(body),
  });
}
export const deactivateSettings = (resource: string, id: number, version: number, motivo: string) =>
  api(`${SETTINGS_PATH}/${resource}/${id}?${new URLSearchParams({ version: String(version), motivo })}`, { method: "DELETE" });
export const getBenefitPreview = (id: number) => api<BenefitPreview>(`${SETTINGS_PATH}/beneficios/${id}/preview`);
export const applyBenefit = (id: number, motivo: string, version: number, firmaAjuste: string) => api<BenefitPreview>(`${SETTINGS_PATH}/beneficios/${id}/aplicar`, {
  method: "POST", body: JSON.stringify({ motivo, version, firmaAjuste }),
});
export const getEnrollmentPlan = (id: number) => api<PensionPlan>(`${SETTINGS_PATH}/planes/matriculas/${id}/preview`);
export const getRequestPlan = (id: number) => api<PensionPlan>(`${SETTINGS_PATH}/planes/solicitudes/${id}/preview`);
export const generateEnrollmentPlan = (id: number, firmaPlan?: string) => api<PensionPlan>(`${SETTINGS_PATH}/planes/matriculas/${id}/generar`, { method: "POST", body: JSON.stringify({ firmaPlan }) });
export const linkLegacyCommitment = (id: number, idCompromiso: number, mes: number | null, motivo: string, version: number, idConcepto?: number) =>
  api<PensionPlan>(`${SETTINGS_PATH}/planes/matriculas/${id}/vincular`, { method: "POST", body: JSON.stringify({ idCompromiso, mes, motivo, version, ...(idConcepto ? { idConcepto } : {}) }) });
export const OFFERS_PATH = `${SETTINGS_PATH}/ofertas`;
export const createOptionalOffers = (body: { idConcepto: number; idMatriculas: number[]; referencia: string; titulo: string; descripcion: string | null; monto: number; fechaVencimiento: string; fechaLimiteAceptacion: string | null }) =>
  api<OptionalChargeOffer[]>(OFFERS_PATH, { method: "POST", body: JSON.stringify(body) });
export const cancelOptionalOffer = (id: number, version: number, motivo: string) => api<OptionalChargeOffer>(`${OFFERS_PATH}/${id}/cancelar`, { method: "POST", body: JSON.stringify({ version, motivo }) });
