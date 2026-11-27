// inscripcion cursos
import { api } from "@/core/api/client";
import type { MisCursos, PeriodoCurso } from "../types";

/** El servidor solo devuelve los cursos del estudiante autenticado. */
export function listarMisCursos(periodo: PeriodoCurso) {
  return api<MisCursos>(`/api/alumnos/me/cursos?periodo=${periodo}`);
}
