import { api } from "@/core/api/client";
import type { EnrollmentPaymentPlan } from "../types";

export type RenewalState = "EN_REVISION" | "APROBADA" | "RECHAZADA";
export type PromotionResult = "PROMOVIDO" | "NO_PROMOVIDO";

type PreviousEnrollment = {
  idMatriculaAnterior: number;
  idAlumno: number;
  codigoAlumno: string;
  alumno: string;
  anioAnterior: number;
  gradoAnterior: number;
};

type RenewalDestination = {
  anioDestino: number;
  gradoDestino: number;
};

export type RenewalOption = PreviousEnrollment & RenewalDestination & {
  promocion: PromotionResult | null;
  secciones: { id: number; nombre: string; vacantes: number }[];
};

export type RenewalRecord = PreviousEnrollment & RenewalDestination & {
  id: number;
  idSeccion: number;
  seccion: string;
  estado: RenewalState;
  codigoMotivo: string | null;
  motivo: string | null;
  idMatricula: number | null;
  fechaCreacion: string;
  fechaDecision: string | null;
  version: number;
};

export type PromotionRecord = PreviousEnrollment & {
  promocion: PromotionResult | null;
  motivo: string | null;
  version: number;
};

export const RENEWALS_PATH = "/api/renovaciones";
export const PROMOTIONS_PATH = `${RENEWALS_PATH}/promociones`;

export const getRenewalOptions = () => api<RenewalOption[]>(`${RENEWALS_PATH}/opciones`);
export const getRenewal = (id: number) => api<RenewalRecord>(`${RENEWALS_PATH}/${id}`);
export const getRenewalPlan = (id: number) => api<EnrollmentPaymentPlan>(`${RENEWALS_PATH}/${id}/plan`);

export function requestRenewal(idMatriculaAnterior: number, idSeccion: number) {
  return api<RenewalRecord>(RENEWALS_PATH, {
    method: "POST",
    body: JSON.stringify({ idMatriculaAnterior, idSeccion }),
  });
}

export function approveRenewal(record: RenewalRecord, firmaPlan?: string) {
  return api<RenewalRecord>(`${RENEWALS_PATH}/${record.id}/aprobar`, {
    method: "POST",
    body: JSON.stringify({ version: record.version, firmaPlan }),
  });
}

export function rejectRenewal(record: RenewalRecord, motivo: string) {
  return api<RenewalRecord>(`${RENEWALS_PATH}/${record.id}/rechazar`, {
    method: "POST",
    body: JSON.stringify({ version: record.version, motivo }),
  });
}

export function savePromotion(record: PromotionRecord, resultado: PromotionResult, motivo: string) {
  return api<PromotionRecord>(`${PROMOTIONS_PATH}/${record.idMatriculaAnterior}`, {
    method: "PUT",
    body: JSON.stringify({ resultado, motivo, version: record.version }),
  });
}
