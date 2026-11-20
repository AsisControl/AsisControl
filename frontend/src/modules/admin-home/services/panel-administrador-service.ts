import { api } from "@/core/api/client";
import type { PanelIndicadores } from "../types";

export const PANEL_INDICADORES_PATH = "/api/dashboard";

export const obtenerPanelIndicadores = () => api<PanelIndicadores>(PANEL_INDICADORES_PATH);
