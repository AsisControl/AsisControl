import { api } from "@/core/api/client";
import type {
  CorreccionAsistencia,
  CursoSesiones,
  Justificacion,
  Marca,
  SesionAsistencia,
  SesionClase,
} from "../types";

const SESIONES_PATH = "/api/asistencias/sesiones";

/** Nombre del curso para el encabezado: el historial solo trae sus sesiones. */
export function listarCursosDocente() {
  return api<CursoSesiones[]>("/api/asistencias/asignaciones?periodo=TODOS");
}

export function listarSesiones(asignacionId: number) {
  return api<SesionClase[]>(`/api/asistencias/asignaciones/${asignacionId}/sesiones`);
}

export function obtenerSesion(id: number) {
  return api<SesionAsistencia>(`${SESIONES_PATH}/${id}`);
}

export function guardarBorrador(id: number, marcas: Marca[]) {
  return api<SesionAsistencia>(`${SESIONES_PATH}/${id}/borrador`, {
    method: "PUT",
    body: JSON.stringify({ marcas }),
  });
}

export function finalizarAsistencia(id: number, marcas: Marca[], version: number) {
  return api<SesionAsistencia>(`${SESIONES_PATH}/${id}/finalizar`, {
    method: "POST",
    body: JSON.stringify({ marcas, version }),
  });
}

export function corregirAsistencia(id: number, marcas: Marca[], motivo: string, version: number) {
  return api<SesionAsistencia>(`${SESIONES_PATH}/${id}`, {
    method: "PUT",
    body: JSON.stringify({ marcas, motivo, version }),
  });
}

export function listarCorrecciones(id: number) {
  return api<CorreccionAsistencia[]>(`${SESIONES_PATH}/${id}/correcciones`);
}

export function obtenerJustificacion(id: number) {
  return api<Justificacion>(`/api/justificaciones-asistencia/${id}`);
}
