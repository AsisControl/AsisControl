import { api } from "@/core/api/client";
import { normalizePage } from "@/shared/lib/pagination";
import type { Page } from "@/shared/types/api";
import type { AssociationInput, CourseCompetency } from "../../types";
export const ASOCIACIONES_PATH = "/api/cursos-competencias";
/** El backend no filtra por curso ni competencia: se traen en una sola página. */
const ALL = "size=1000";
/** Asociaciones vigentes (curso y competencia activos), ordenadas por curso y orden. */
export async function listarAsociaciones() {
  const page = normalizePage(
    await api<Page<CourseCompetency>>(`${ASOCIACIONES_PATH}?${ALL}`),
  );
  return page.content
    .filter((item) => item.activo)
    .sort(
      (a, b) =>
        a.cursoNombre.localeCompare(b.cursoNombre, "es") || a.orden - b.orden,
    );
}
/** Agrupa las asociaciones por curso o por competencia (id → lista). */
export function agruparPor(
  list: CourseCompetency[],
  key: "cursoId" | "competenciaId",
) {
  const groups = new Map<number, CourseCompetency[]>();
  for (const item of list)
    groups.set(item[key], [...(groups.get(item[key]) ?? []), item]);
  return groups;
}
/** Si la asociación existió y se quitó, el backend la reactiva (200 en vez de 201). */
export function crearAsociacion(input: AssociationInput) {
  return api<CourseCompetency>(ASOCIACIONES_PATH, {
    method: "POST",
    body: JSON.stringify(input),
  });
}
export function actualizarAsociacion(
  association: CourseCompetency,
  input: AssociationInput,
) {
  return api<CourseCompetency>(`${ASOCIACIONES_PATH}/${association.id}`, {
    method: "PUT",
    body: JSON.stringify({ ...input, version: association.version }),
  });
}
export function quitarAsociacion(id: number) {
  return api<null>(`${ASOCIACIONES_PATH}/${id}`, { method: "DELETE" });
}
/** Opciones de los selectores: cursos y competencias activos. */
export async function listarOpciones() {
  const [courses, competencies] = await Promise.all([
    api<Page<{ id: number; codigo: string; nombre: string }>>(
      `/api/cursos?${ALL}&sort=nombre`,
    ),
    api<Page<{ id: number; codigo: string; nombre: string }>>(
      `/api/competencias?${ALL}&sort=nombre`,
    ),
  ]);
  return {
    courses: normalizePage(courses).content,
    competencies: normalizePage(competencies).content,
  };
}
