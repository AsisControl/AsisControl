import { api } from "./client";

export type Indicador = {
  codigo: string;
  etiqueta: string;
  valor: number;
  tono: "neutral" | "success" | "warning" | "danger";
};

/** Indicadores según el rol autenticado. */
export type PanelIndicadores = {
  indicadores: Indicador[];
};

export const obtenerPanelIndicadores = () => api<PanelIndicadores>("/api/dashboard");
