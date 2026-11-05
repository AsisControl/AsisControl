import { api } from "@/core/api/client";
import type { Page } from "@/shared/types/api";
import type {
  AssignmentState,
  CourseOption,
  SectionOption,
  TeacherAssignment,
  TeacherAssignmentInput,
  TeacherOption,
} from "../../types";
/** Ruta base del recurso; el listado paginado se consume con usePaged. */
export const ASIGNACIONES_PATH = "/api/asignaciones-cursos";
/** Filtros del listado en el formato de usePaged ("estado=ACTIVAS&anioAcademico=2026"). */
export function assignmentFilters(estado: AssignmentState, anio: string) {
  const params = new URLSearchParams({ estado });
  if (anio) params.set("anioAcademico", anio);
  return params.toString();
}
export function crearAsignacion(input: TeacherAssignmentInput) {
  return api<TeacherAssignment>(ASIGNACIONES_PATH, {
    method: "POST",
    body: JSON.stringify(input),
  });
}
export function actualizarAsignacion(
  assignment: TeacherAssignment,
  input: TeacherAssignmentInput,
) {
  return api<TeacherAssignment>(`${ASIGNACIONES_PATH}/${assignment.id}`, {
    method: "PUT",
    body: JSON.stringify({ ...input, version: assignment.version }),
  });
}
/** Retiro con motivo; el backend lo rechaza si el docente tiene bloques de horario (PLA-06). */
export function retirarAsignacion(assignment: TeacherAssignment, motivo: string) {
  return api<TeacherAssignment>(`${ASIGNACIONES_PATH}/${assignment.id}/retirar`, {
    method: "PATCH",
    body: JSON.stringify({ motivo, version: assignment.version }),
  });
}
/** Secciones habilitadas del año, para elegir dónde se dicta el curso. */
export async function seccionesDelAnio(anio: number) {
  const result = await api<Page<SectionOption>>(
    `/api/secciones?activo=true&anioAcademico=${anio}&size=100`,
  );
  return result.content;
}
/** Cursos activos del año y grado de la sección elegida. */
export async function cursosDelGrado(anio: number, grado: number) {
  const result = await api<Page<CourseOption>>(
    `/api/cursos?anio=${anio}&grado=${grado}&size=100`,
  );
  return result.content;
}
/** Docentes activos. */
export async function docentesActivos() {
  const result = await api<Page<TeacherOption>>("/api/docentes?size=100");
  return result.content;
}
