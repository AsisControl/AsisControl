import { api } from "@/core/api/client";
import { normalizePage } from "@/shared/lib/pagination";
import type { Page } from "@/shared/types/api";
import type {
  Classroom,
  CourseAssignment,
  ScheduleBlock,
  ScheduleBlockInput,
  ScheduleSection,
} from "../../types";
export const HORARIOS_PATH = "/api/horarios";
const PAGE_SIZE = 100;
/** Recorre todas las páginas: la primera no representa toda la matriz. */
async function listAll<T>(path: string): Promise<T[]> {
  const items: T[] = [];
  for (let page = 0; ; page++) {
    const separator = path.includes("?") ? "&" : "?";
    const data = normalizePage(
      await api<Page<T>>(`${path}${separator}page=${page}&size=${PAGE_SIZE}`),
    );
    items.push(...data.content);
    if (data.last || !data.content.length) return items;
  }
}
/** Filtros de GET /api/horarios; se combinan con AND en el servidor. */
export type ScheduleFilters = {
  anioAcademico: number;
  seccionId?: number;
  docenteId?: number;
  aulaId?: number;
};
export function listarHorarios(filters: ScheduleFilters) {
  const query = new URLSearchParams(
    Object.entries(filters)
      .filter(([, value]) => value !== undefined)
      .map(([key, value]) => [key, String(value)]),
  );
  return listAll<ScheduleBlock>(`${HORARIOS_PATH}?${query}`);
}
/** Secciones habilitadas de todos los años; el año se elige en la pantalla. */
export function listarSeccionesActivas() {
  return listAll<ScheduleSection>("/api/secciones?activo=true");
}
/** Solo devuelve asignaciones activas; se filtran por sección en la pantalla. */
export function listarAsignaciones() {
  return listAll<CourseAssignment>("/api/asignaciones-cursos");
}
/** Sin estado, el backend lista solo las aulas habilitadas. */
export function listarAulasHabilitadas() {
  return listAll<Classroom>("/api/aulas?sort=codigo");
}
export function crearHorario(input: ScheduleBlockInput) {
  return api<ScheduleBlock>(HORARIOS_PATH, {
    method: "POST",
    body: JSON.stringify(input),
  });
}
export function actualizarHorario(block: ScheduleBlock, input: ScheduleBlockInput) {
  return api<ScheduleBlock>(`${HORARIOS_PATH}/${block.id}`, {
    method: "PUT",
    body: JSON.stringify({ ...input, version: block.version }),
  });
}
/** Baja lógica: el bloque deja de aparecer en la matriz. */
export function eliminarHorario(id: number) {
  return api<null>(`${HORARIOS_PATH}/${id}`, { method: "DELETE" });
}
