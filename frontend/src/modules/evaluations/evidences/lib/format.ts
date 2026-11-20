import { shortDateLabel } from "@/shared/lib/formatters";
import type { EvidenceCompetency } from "../types";

/** Fecha de Lima de un instante UTC del servidor ("…T04:01:54.728339Z"); "—" si falta o es inválido. */
export function evidenceDate(instant: string | null | undefined) {
  if (!instant || Number.isNaN(new Date(instant).getTime())) return "—";
  return shortDateLabel(instant);
}

/** "Matemática (MAT-C1)": el código distingue competencias de igual nombre. */
export function competencyLabel(competency: EvidenceCompetency) {
  return competency.competenciaCodigo
    ? `${competency.competenciaNombre} (${competency.competenciaCodigo})`
    : competency.competenciaNombre;
}
