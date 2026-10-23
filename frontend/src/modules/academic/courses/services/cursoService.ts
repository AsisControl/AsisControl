import { api } from "@/core/api/client";
import type { Course, CourseInput } from "../../types";
/** Ruta base del recurso; el listado paginado se consume con usePaged. */
export const CURSOS_PATH = "/api/cursos";
export function crearCurso(input: CourseInput) {
  return api<Course>(CURSOS_PATH, {
    method: "POST",
    body: JSON.stringify(input),
  });
}
export function actualizarCurso(course: Course, input: CourseInput) {
  return api<Course>(`${CURSOS_PATH}/${course.id}`, {
    method: "PUT",
    body: JSON.stringify({ ...input, version: course.version }),
  });
}
/** El backend no borra el curso: lo marca inactivo y deja de listarlo. */
export function desactivarCurso(id: number) {
  return api<null>(`${CURSOS_PATH}/${id}`, { method: "DELETE" });
}
