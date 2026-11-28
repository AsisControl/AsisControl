import { api } from "@/core/api/client";
import type {
  Evidence,
  EvidenceEdit,
  EvidenceInput,
  EvidencePeriod,
  EvidenceStudent,
} from "../types";

export const EVIDENCIAS_PATH = "/api/evidencias-pedagogicas";

export function crearEvidencia(input: EvidenceInput) {
  return api<Evidence>(EVIDENCIAS_PATH, { method: "POST", body: JSON.stringify(input) });
}
export function editarEvidencia(id: number, input: EvidenceEdit) {
  return api<Evidence>(`${EVIDENCIAS_PATH}/${id}`, { method: "PUT", body: JSON.stringify(input) });
}
export function vincularEvidencia(id: number, evaluacionId: number) {
  return api<null>(`${EVIDENCIAS_PATH}/${id}/evaluaciones/${evaluacionId}`, { method: "PUT" });
}
export function inactivarEvidencia(id: number) {
  return api<null>(`${EVIDENCIAS_PATH}/${id}`, { method: "DELETE" });
}
/** Períodos activos del año de la asignación (los mismos que valida el backend). */
export function listarPeriodos(asignacionId: number) {
  return api<EvidencePeriod[]>(`/api/evaluaciones/periodos-informe?idAsignacionCurso=${asignacionId}`);
}
export function listarEstudiantes(asignacionId: number) {
  return api<EvidenceStudent[]>(`${EVIDENCIAS_PATH}/asignaciones/${asignacionId}/estudiantes`);
}
/** Filtros del listado; usePaged los agrega tras page y size. */
export function evidenceFilters(filters: {
  asignacionId: string;
  alumnoId: string;
  competenciaId?: string;
  periodoId?: string;
}) {
  const entries = Object.entries(filters).filter(([, value]) => value);
  return new URLSearchParams(entries as [string, string][]).toString();
}
