import { api, apiBlob } from "@/core/api/client";
import type { PreviousStudyPlan, StudyPlan, StudyPlanInput, StudyPlanName } from "../types";

export const ANIOS_ACADEMICOS_PATH = "/api/configuracion-academica/anios";
const PLANES_ESTUDIOS_PATH = "/api/planes-estudios";
export const PLANES_ESTUDIOS_IMPORT_PATH = `${PLANES_ESTUDIOS_PATH}/importaciones`;

export function obtenerPlanEstudios(anio: number, grado: number) {
  return api<StudyPlan>(`${PLANES_ESTUDIOS_PATH}/${anio}/${grado}`);
}

export function consultarPlanAnterior(anio: number, grado: number) {
  return api<PreviousStudyPlan>(`${PLANES_ESTUDIOS_PATH}/${anio}/${grado}/anterior`);
}

export function guardarPlanEstudios(anio: number, grado: number, input: StudyPlanInput) {
  return api<StudyPlan>(`${PLANES_ESTUDIOS_PATH}/${anio}/${grado}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export function obtenerNombrePlan(anio: number) {
  return api<StudyPlanName>(`${PLANES_ESTUDIOS_PATH}/${anio}/nombre`);
}

export function actualizarNombrePlan(anio: number, input: StudyPlanName) {
  return api<StudyPlanName>(`${PLANES_ESTUDIOS_PATH}/${anio}/nombre`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export function descargarPlanEstudios(anio: number) {
  return apiBlob(`${PLANES_ESTUDIOS_PATH}/${anio}/pdf`, { timeoutMs: 60000 });
}
