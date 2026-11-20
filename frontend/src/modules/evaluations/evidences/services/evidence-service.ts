import { api } from "@/core/api/client";
import { listProgressEvaluations } from "../../services/progress-service";
import { obtenerPlanilla } from "../../services/evaluation-service";
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
export function inactivarEvidencia(id: number) {
  return api<null>(`${EVIDENCIAS_PATH}/${id}`, { method: "DELETE" });
}
/** Períodos activos del año de la asignación (los mismos que valida el backend). */
export function listarPeriodos(asignacionId: number) {
  return api<EvidencePeriod[]>(`/api/evaluaciones/periodos-informe?idAsignacionCurso=${asignacionId}`);
}
/**
 * El backend no tiene un endpoint de estudiantes por asignación. La única fuente propia del
 * docente es la planilla de una evaluación de esa asignación (solo matrículas activas).
 * Devuelve `null` cuando aún no hay ninguna evaluación de la cual obtenerlos.
 */
export async function listarEstudiantes(asignacionId: number): Promise<EvidenceStudent[] | null> {
  const [evaluation] = await listProgressEvaluations(asignacionId);
  if (!evaluation) return null;
  const sheet = await obtenerPlanilla(evaluation.id);
  return sheet.alumnos.map((item) => ({ id: item.idAlumno, nombre: item.nombreCompleto }));
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
