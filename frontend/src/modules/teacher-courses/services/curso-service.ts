import { api } from "@/core/api/client";
import type { CursoAsignado, PeriodoCurso } from "../types";

/** Cursos del docente autenticado; un año en borrador nunca llega desde el backend. */
export function listarMisCursos(periodo: PeriodoCurso) {
  return api<CursoAsignado[]>(`/api/asistencias/asignaciones?periodo=${periodo}`);
}
