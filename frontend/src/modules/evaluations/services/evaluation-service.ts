import { api } from "@/core/api/client";
import { normalizePage } from "@/shared/lib/pagination";
import type { Page } from "@/shared/types/api";
import type {
  AchievementLevel,
  ConsolidatedLevel,
  CorrectionEntry,
  CourseCompetency,
  Evaluation,
  EvaluationInput,
  EvaluationState,
  GradeSheetData,
  OwnAssignment,
} from "../types";

export const EVALUACIONES_PATH = "/api/evaluaciones";
const CALIFICACIONES_PATH = "/api/calificaciones";

export type EvaluationFilters = {
  periodo?: string;
  idCurso?: string;
  idSeccion?: string;
  estado?: string;
  busqueda?: string;
};
/** Ruta del listado con los filtros que tienen valor; usePaged agrega la paginación. */
export function evaluationsPath(filters: EvaluationFilters) {
  const entries = Object.entries(filters).filter(([, value]) => value);
  const query = new URLSearchParams(entries as [string, string][]).toString();
  return query ? `${EVALUACIONES_PATH}?${query}` : EVALUACIONES_PATH;
}
export async function listarEvaluaciones(filters: EvaluationFilters) {
  const separator = evaluationsPath(filters).includes("?") ? "&" : "?";
  const page = await api<Page<Evaluation>>(`${evaluationsPath(filters)}${separator}size=100`);
  return normalizePage(page).content;
}
export function obtenerEvaluacion(id: number) {
  return api<Evaluation>(`${EVALUACIONES_PATH}/${id}`);
}
export function crearEvaluacion(input: EvaluationInput) {
  return api<Evaluation>(EVALUACIONES_PATH, { method: "POST", body: JSON.stringify(input) });
}
export function actualizarEvaluacion(id: number, input: EvaluationInput) {
  return api<Evaluation>(`${EVALUACIONES_PATH}/${id}`, { method: "PUT", body: JSON.stringify(input) });
}
export function cambiarEstadoEvaluacion(id: number, estado: EvaluationState) {
  return api<Evaluation>(`${EVALUACIONES_PATH}/${id}/estado`, {
    method: "PUT",
    body: JSON.stringify({ estado }),
  });
}
export function eliminarEvaluacion(id: number) {
  return api<null>(`${EVALUACIONES_PATH}/${id}`, { method: "DELETE" });
}
export function obtenerPlanilla(id: number) {
  return api<GradeSheetData>(`${EVALUACIONES_PATH}/${id}/planilla`);
}
export type DraftGrade = {
  idEvaluacion: number;
  idAlumno: number;
  idCompetencia: number;
  nivelLogro: AchievementLevel;
};
/** Guarda los niveles de una evaluación en borrador. */
export function guardarNiveles(grades: DraftGrade[]) {
  return api<unknown[]>(`${CALIFICACIONES_PATH}/lote`, {
    method: "PUT",
    body: JSON.stringify({
      calificaciones: grades.map((grade) => ({ ...grade, observacion: null })),
    }),
  });
}
export type GradeCorrection = {
  idCalificacion: number;
  nivelLogro: AchievementLevel;
  version: number;
};
/** US-50: corrige niveles publicados con un motivo común. */
export function corregirNiveles(motivo: string, correcciones: GradeCorrection[]) {
  return api<unknown[]>(`${CALIFICACIONES_PATH}/correcciones`, {
    method: "POST",
    body: JSON.stringify({ motivo, correcciones }),
  });
}
export function obtenerCorrecciones(idCalificacion: number) {
  return api<CorrectionEntry[]>(`${CALIFICACIONES_PATH}/${idCalificacion}/correcciones`);
}
export function obtenerConsolidado(idAlumno: number, idAsignacionCurso: number) {
  return api<ConsolidatedLevel[]>(
    `${CALIFICACIONES_PATH}/consolidados?idAlumno=${idAlumno}&idAsignacionCurso=${idAsignacionCurso}`,
  );
}
export async function listarAsignacionesPropias() {
  const page = await api<Page<OwnAssignment>>("/api/asignaciones-cursos/mias?size=100");
  return normalizePage(page).content;
}
/** El backend no filtra las asociaciones por curso: se filtran aquí. */
export async function listarCompetenciasDelCurso(cursoId: number) {
  const page = await api<Page<CourseCompetency>>("/api/cursos-competencias?size=1000");
  return normalizePage(page)
    .content.filter((item) => item.cursoId === cursoId && item.activo)
    .sort((a, b) => a.orden - b.orden);
}
