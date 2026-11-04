import { api } from "@/core/api/client";
import type { AlumnoResumen, CalificarEntregaRequest, EntregaTareaResponse, SeguimientoTareaResponse } from "../types";

export const entregasPath = (tareaId: number) => `/api/tareas/${tareaId}/entregas`;

export function obtenerEntrega(id: number) {
  return api<EntregaTareaResponse>(`/api/entregas/${id}`);
}

export function calificarEntrega(id: number, input: CalificarEntregaRequest) {
  return api<EntregaTareaResponse>(`/api/entregas/${id}/calificar`, {
    method: "POST", body: JSON.stringify(input),
  });
}

/** Padrón de la sección con quién entregó y quién no; solo docente de la tarea y dirección. */
export function obtenerSeguimiento(tareaId: number) {
  return api<SeguimientoTareaResponse>(`/api/tareas/${tareaId}/seguimiento`);
}

export function obtenerAlumnoResumen(id: number) {
  return api<AlumnoResumen>(`/api/alumnos/${id}`);
}
