// asistencia alumnos
import { api } from "@/core/api/client";
import { normalizePage } from "@/shared/lib/pagination";
import type { Page } from "@/shared/types/api";
import type { AsistenciaEstudiante, BloqueHorario, EstudianteFamilia } from "../types";

export function listarEstudiantes() {
  return api<EstudianteFamilia[]>("/api/familia/estudiantes");
}

/**
 * Trae todo el historial: los filtros y los totales de la pantalla se calculan sobre él.
 * Sin alumnoId (vista del alumno) el backend devuelve solo la asistencia de quien inició sesión.
 */
export async function listarAsistencias(alumnoId?: number) {
  const params = new URLSearchParams({ size: "2000", sort: "fechaRegistro,desc" });
  if (alumnoId !== undefined) params.set("alumnoId", String(alumnoId));
  const pagina = normalizePage(await api<Page<AsistenciaEstudiante>>(`/api/asistencias/alumnos?${params}`));
  return pagina.content;
}

/** Horario del alumno autenticado: el servidor lo limita a sus matrículas activas. */
export function listarHorarioPropio(anioAcademico: number) {
  return api<BloqueHorario[]>(`/api/horarios/mio?anioAcademico=${anioAcademico}`);
}

export function enviarJustificacion(asistenciaAlumnoId: number, motivo: string, archivoIds: number[]) {
  return api("/api/justificaciones-asistencia", {
    method: "POST",
    body: JSON.stringify({ asistenciaAlumnoId, motivo, archivoIds }),
  });
}
