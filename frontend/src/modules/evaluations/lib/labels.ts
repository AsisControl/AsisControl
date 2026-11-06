import type { StatusTone } from "@/shared/ui";
import { shortDateLabel } from "@/shared/lib/formatters";
import type { AchievementLevel, EvaluationPeriod, EvaluationState, OwnAssignment } from "../types";

export const LEVELS: AchievementLevel[] = ["AD", "A", "B", "C"];
export const LEVEL_SCALE =
  "Escala: AD = Logro destacado · A = Logro esperado · B = En proceso · C = En inicio";
export const PERIODS: Record<EvaluationPeriod, string> = {
  DIARIO: "Diario",
  SEMANAL: "Semanal",
  MENSUAL: "Mensual",
  BIMESTRAL: "Bimestral",
  TRIMESTRAL: "Trimestral",
  PERIODO_ACADEMICO: "Período académico",
  ANUAL: "Anual",
};
export const STATE_TONES: Record<EvaluationState, StatusTone> = {
  BORRADOR: "warning",
  PUBLICADA: "success",
  CERRADA: "neutral",
  ANULADA: "danger",
};
/** LocalDate sin zona: se fija el mediodía para que no cambie de día en Lima. */
export const evaluationDate = (date: string) => shortDateLabel(`${date}T12:00:00-05:00`);
export const sectionOf = (item: OwnAssignment) => `${item.grado}° ${item.seccion}`;
/** Cursos sin repetir; el nombre se repite entre grados, por eso se indica el grado. */
export function courseOptions(assignments: OwnAssignment[]) {
  const options = new Map<number, string>();
  for (const item of assignments) options.set(item.cursoId, `${item.curso} · ${item.grado}° grado`);
  return [...options].sort((a, b) => a[1].localeCompare(b[1]));
}
export function sectionOptions(assignments: OwnAssignment[], cursoId: string) {
  const options = new Map<number, string>();
  for (const item of assignments)
    if (!cursoId || String(item.cursoId) === cursoId)
      options.set(item.seccionId, `${sectionOf(item)} · ${item.anioAcademico}`);
  return [...options].sort((a, b) => a[1].localeCompare(b[1]));
}
