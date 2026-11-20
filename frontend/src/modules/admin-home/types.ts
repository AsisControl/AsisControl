export type TonoIndicador = "neutral" | "success" | "warning" | "danger";

export type Indicador = {
  codigo: string;
  etiqueta: string;
  valor: number;
  tono: TonoIndicador;
  rutaDestino: string | null;
};

/** Respuesta de GET /api/dashboard (indicadores según el rol autenticado). */
export type PanelIndicadores = {
  rol: string;
  personaId: number | null;
  generadoEn: string;
  indicadores: Indicador[];
};
