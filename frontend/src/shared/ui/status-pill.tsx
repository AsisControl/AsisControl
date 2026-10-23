import { stateLabel } from "@/shared/lib/formatters";
export type StatusTone = "success" | "info" | "warning" | "danger" | "neutral";
/** Color de cada estado de solicitudes y matrículas; uno nuevo usa el neutro. */
const tones: Record<string, StatusTone> = {
  BORRADOR: "neutral",
  ENVIADA: "info",
  EN_REVISION: "warning",
  OBSERVADA: "warning",
  RECHAZADA: "danger",
  CANCELADA: "neutral",
  MATRICULA_FINALIZADA: "success",
  ACTIVA: "success",
  RETIRADA: "neutral",
  FINALIZADA: "info",
  ANULADA: "danger",
};
export const toneOf = (state: string) => tones[state] ?? "neutral";
/** Etiqueta de estado: el texto sale del código del backend ("EN_REVISION" → "En revision"). */
export function StatusPill({
  state,
  label,
  tone,
}: {
  state: string;
  /** Texto propio cuando el código no se lee bien ("Activo"). */
  label?: string;
  tone?: StatusTone;
}) {
  return (
    <span className={`status status-${tone ?? toneOf(state)}`}>
      {label ?? stateLabel(state)}
    </span>
  );
}
