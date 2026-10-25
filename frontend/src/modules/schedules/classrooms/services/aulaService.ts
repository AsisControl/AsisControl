import { api } from "@/core/api/client";
import type {
  Classroom,
  ClassroomInput,
  ClassroomSchedule,
  ClassroomSummary,
} from "../../types";
/** Ruta base del recurso; el listado paginado se consume con usePaged. */
export const AULAS_PATH = "/api/aulas";
/** El backend lista solo las habilitadas si no se indica el estado. */
export const AULAS_LIST_PATH = `${AULAS_PATH}?estado=TODAS&sort=codigo`;
export function crearAula(input: ClassroomInput) {
  return api<Classroom>(AULAS_PATH, {
    method: "POST",
    body: JSON.stringify(input),
  });
}
export function actualizarAula(classroom: Classroom, input: ClassroomInput) {
  return api<Classroom>(`${AULAS_PATH}/${classroom.id}`, {
    method: "PUT",
    body: JSON.stringify({ ...input, version: classroom.version }),
  });
}
/** Inhabilitar exige motivo; el backend lo rechaza si el aula tiene horarios activos. */
export function inhabilitarAula(classroom: Classroom, motivo: string) {
  return api<Classroom>(`${AULAS_PATH}/${classroom.id}/inhabilitar`, {
    method: "PATCH",
    body: JSON.stringify({ motivo, version: classroom.version }),
  });
}
export function habilitarAula(classroom: Classroom, motivo: string | null) {
  return api<Classroom>(`${AULAS_PATH}/${classroom.id}/habilitar`, {
    method: "PATCH",
    body: JSON.stringify({ motivo, version: classroom.version }),
  });
}
/** Borrado físico: el backend solo lo permite si el aula nunca se usó en un horario. */
export function eliminarAula(id: number) {
  return api<null>(`${AULAS_PATH}/${id}`, { method: "DELETE" });
}
export function resumenAulas() {
  return api<ClassroomSummary>(`${AULAS_PATH}/resumen`);
}
/** Horarios activos e inactivos que usan el aula. */
export function horariosDeAula(id: number) {
  return api<ClassroomSchedule[]>(`${AULAS_PATH}/${id}/horarios`);
}
