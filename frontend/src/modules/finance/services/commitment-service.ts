import { api } from "@/core/api/client";
import type { Page } from "@/shared/types/api";
import type {
  CommitmentChange,
  CommitmentDetailWithMovements,
  CommitmentInput,
  CommitmentSummary,
  PaymentCommitment,
  SectionChoice,
} from "../types";

export const COMMITMENTS_PATH = "/api/compromisos-pago";
export const ELIGIBLE_ENROLLMENTS_PATH = `${COMMITMENTS_PATH}/matriculas`;
export const COMMITMENT_SUMMARY_PATH = `${COMMITMENTS_PATH}/resumen`;
export const TRANSACTIONS_PATH = "/api/transacciones-pago";

export const getCommitment = (id: number) =>
  api<PaymentCommitment>(`${COMMITMENTS_PATH}/${id}`);

/** Secuencia de transacciones del compromiso con sus comprobantes internos. */
export const getCommitmentMovements = (id: number) =>
  api<CommitmentDetailWithMovements>(`${COMMITMENTS_PATH}/${id}/detalle`);

/** Cantidades y montos por estado e indicadores de morosidad con los mismos filtros del listado. */
export const getCommitmentSummary = (filters: string) =>
  api<CommitmentSummary>(`${COMMITMENT_SUMMARY_PATH}${filters ? `?${filters}` : ""}`);

/** Secciones del año para filtrar por grado y sección; puede no estar permitido para todos los roles. */
export async function getSectionsOfYear(year: number) {
  const result = await api<Page<SectionChoice>>(
    `/api/secciones?activo=true&anioAcademico=${year}&size=100`,
  );
  return result.content;
}

export const issueCommitment = (input: CommitmentInput) =>
  api<PaymentCommitment>(COMMITMENTS_PATH, {
    method: "POST",
    body: JSON.stringify(input),
  });

export const updateCommitment = (id: number, change: CommitmentChange) =>
  api<PaymentCommitment>(`${COMMITMENTS_PATH}/${id}`, {
    method: "PUT",
    body: JSON.stringify(change),
  });

export const annulCommitment = (id: number, motivo: string, version: number) =>
  api<PaymentCommitment>(`${COMMITMENTS_PATH}/${id}/anulacion`, {
    method: "POST",
    body: JSON.stringify({ motivo, version }),
  });
