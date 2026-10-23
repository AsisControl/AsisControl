import { api } from "@/core/api/client";
import type { Competency, CompetencyInput } from "../../types";
export const COMPETENCIAS_PATH = "/api/competencias";
/** El backend busca con el parámetro "busqueda" (código o nombre). */
export const competenciasPath = (search: string) =>
  search
    ? `${COMPETENCIAS_PATH}?busqueda=${encodeURIComponent(search)}`
    : COMPETENCIAS_PATH;
export function crearCompetencia(input: CompetencyInput) {
  return api<Competency>(COMPETENCIAS_PATH, {
    method: "POST",
    body: JSON.stringify(input),
  });
}
export function actualizarCompetencia(
  competency: Competency,
  input: CompetencyInput,
) {
  return api<Competency>(`${COMPETENCIAS_PATH}/${competency.id}`, {
    method: "PUT",
    body: JSON.stringify({ ...input, version: competency.version }),
  });
}
/** El backend no la borra: la marca inactiva y deja de listarla. */
export function desactivarCompetencia(id: number) {
  return api<null>(`${COMPETENCIAS_PATH}/${id}`, { method: "DELETE" });
}
