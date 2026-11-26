import { api } from "@/core/api/client";
import { normalizePage } from "@/shared/lib/pagination";
import type { Page } from "@/shared/types/api";
import type { AsignacionCursoResponse, TareaResponse } from "../types";
import type { CrearTareaRequest, ActualizarTareaRequest } from "../types";

export function crearTarea(input: CrearTareaRequest) {
  return api<TareaResponse>(TAREAS_PATH, { method: "POST", body: JSON.stringify(input) });
}

export function actualizarTarea(id: number, input: ActualizarTareaRequest) {
  return api<TareaResponse>(`${TAREAS_PATH}/${id}`, { method: "PUT", body: JSON.stringify(input) });
}

export function publicarTarea(id: number) {
  return api<TareaResponse>(`${TAREAS_PATH}/${id}/publicar`, { method: "POST" });
}

export function cerrarTarea(id: number) {
  return api<TareaResponse>(`${TAREAS_PATH}/${id}/cerrar`, { method: "POST" });
}

export function archivarTarea(id: number) {
  return api<TareaResponse>(`${TAREAS_PATH}/${id}/archivar`, { method: "POST" });
}

export const TAREAS_PATH = "/api/tareas";

export type MissingZeros = { estudiantes: number; calificaciones: number };

export type MissingDeliveries = {
  alumnos: { id: number; nombre: string; codigo: string | null; competencias: number[] }[];
  competencias: { id: number; nombre: string }[];
  maximo: number;
};

export function listMissingDeliveries(id: number) {
  return api<MissingDeliveries>(`${TAREAS_PATH}/${id}/calificaciones-sin-entrega`);
}

export function gradeMissingDeliveries(id: number, alumnos: number[], competencias: number[], nota: string) {
  return api<MissingZeros>(`${TAREAS_PATH}/${id}/calificaciones-sin-entrega`, {
    method: "POST", body: JSON.stringify({ alumnos, competencias, nota }),
  });
}

export async function listarTareas(page = 0, size = 20, asignacionCursoId?: number) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (asignacionCursoId !== undefined)
    params.set("asignacionCursoId", String(asignacionCursoId));
  return normalizePage(await api<Page<TareaResponse>>(`${TAREAS_PATH}?${params}`));
}

export function obtenerTarea(id: number) {
  return api<TareaResponse>(`${TAREAS_PATH}/${id}`);
}

/** Baja lógica en cualquier estado; si la tarea tiene entregas el backend exige el motivo. */
export function eliminarTarea(id: number, motivo: string | null) {
  return api<null>(`${TAREAS_PATH}/${id}`, { method: "DELETE", body: JSON.stringify({ motivo }) });
}

/** Asignaciones activas del docente autenticado: nunca el catálogo global. */
export async function listarAsignacionesPropias() {
  const assignments: AsignacionCursoResponse[] = [];
  let page = 0;
  let last: boolean;
  do {
    const result = normalizePage(await api<Page<AsignacionCursoResponse>>(
      `/api/asignaciones-cursos/mias?size=100&page=${page}`,
    ));
    assignments.push(...result.content);
    last = result.last;
    page++;
  } while (!last);
  return assignments;
}

/** Cantidad de entregas registradas; decide si cambiar el vencimiento o eliminar exige motivo. */
export async function contarEntregas(tareaId: number) {
  return normalizePage(await api<Page<unknown>>(`${TAREAS_PATH}/${tareaId}/entregas?page=0&size=1`)).totalElements;
}

/** Solo usar para IDs presentes en tareas devueltas al docente por el backend. */
export function obtenerContextoAsignacion(id: number) {
  return api<AsignacionCursoResponse>(`/api/asignaciones-cursos/${id}`);
}
