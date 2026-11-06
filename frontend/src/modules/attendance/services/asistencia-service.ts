import { api } from "@/core/api/client";
import { normalizePage } from "@/shared/lib/pagination";
import type { Page } from "@/shared/types/api";
import type {
  CorreccionAsistencia,
  CursoSesiones,
  EstadoJustificacion,
  Justificacion,
  Marca,
  SesionAsistencia,
  SesionClase,
} from "../types";

const SESIONES_PATH = "/api/asistencias/sesiones";
const JUSTIFICACIONES_PATH = "/api/justificaciones-asistencia";

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
  return api<Justificacion>(`${JUSTIFICACIONES_PATH}/${id}`);
}

export async function listarJustificaciones(estados: EstadoJustificacion[], orden: string, pagina: number) {
  const params = new URLSearchParams({ page: String(pagina), size: "20", sort: orden });
  estados.forEach((estado) => params.append("estado", estado));
  return normalizePage(await api<Page<Justificacion>>(`${JUSTIFICACIONES_PATH}?${params}`));
}

export function aprobarJustificacion(id: number, version: number) {
  return api<Justificacion>(`${JUSTIFICACIONES_PATH}/${id}/aprobar`, {
    method: "POST",
    body: JSON.stringify({ version }),
  });
}

export function rechazarJustificacion(id: number, version: number, observacion: string) {
  return api<Justificacion>(`${JUSTIFICACIONES_PATH}/${id}/rechazar`, {
    method: "POST",
    body: JSON.stringify({ version, observacion }),
  });
}
