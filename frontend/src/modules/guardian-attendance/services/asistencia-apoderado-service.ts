// asistencia alumnos
import { api } from "@/core/api/client";
import { normalizePage } from "@/shared/lib/pagination";
import type { Page } from "@/shared/types/api";
import type { AsistenciaEstudiante, EstudianteFamilia } from "../types";

export function listarEstudiantes() {
  return api<EstudianteFamilia[]>("/api/familia/estudiantes");
}

/** Trae todo el historial: los filtros y los totales de la pantalla se calculan sobre él. */
export async function listarAsistencias(alumnoId: number) {
  const params = new URLSearchParams({
    alumnoId: String(alumnoId),
    size: "2000",
    sort: "fechaRegistro,desc",
  });
  const pagina = normalizePage(await api<Page<AsistenciaEstudiante>>(`/api/asistencias/alumnos?${params}`));
  return pagina.content;
}

export function enviarJustificacion(asistenciaAlumnoId: number, motivo: string, archivoIds: number[]) {
  return api("/api/justificaciones-asistencia", {
    method: "POST",
    body: JSON.stringify({ asistenciaAlumnoId, motivo, archivoIds }),
  });
}
