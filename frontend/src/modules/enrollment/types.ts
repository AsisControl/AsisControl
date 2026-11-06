/** Valor legible de un campo antes y después del cambio; null si no existía. */
export type HistoryField = {
  campo: string;
  anterior: string | null;
  nuevo: string | null;
};
export type History = {
  id: number;
  actor: string;
  /** null para accesos que ya no están activos. */
  actorNombre: string | null;
  rol: string | null;
  fecha: string;
  accion: string;
  motivo: string | null;
  campos: HistoryField[];
};
