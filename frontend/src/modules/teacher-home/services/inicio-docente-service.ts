import { api } from "@/core/api/client";
import type { InicioDocente } from "../types";

export const INICIO_DOCENTE_PATH = "/api/dashboard/docente";

export const obtenerInicioDocente = () => api<InicioDocente>(INICIO_DOCENTE_PATH);
