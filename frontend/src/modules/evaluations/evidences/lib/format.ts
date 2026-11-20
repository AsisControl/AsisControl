import { shortDateLabel } from "@/shared/lib/formatters";

/** Fecha de Lima de un instante UTC del servidor ("…T04:01:54.728339Z"); "—" si falta o es inválido. */
export function evidenceDate(instant: string | null | undefined) {
  if (!instant || Number.isNaN(new Date(instant).getTime())) return "—";
  return shortDateLabel(instant);
}
