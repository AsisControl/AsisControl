// inscripcion cursos
import { api } from "@/core/api/client";
import type { MisCursos } from "../types";

/** El servidor solo devuelve los cursos del estudiante autenticado. */
export function listarMisCursos() {
  return api<MisCursos>("/api/alumnos/me/cursos");
}
