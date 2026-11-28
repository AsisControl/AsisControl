import type { Page } from "@/shared/types/api";
import type { Evaluation } from "../types";

/**
 * El listado del servidor aún no admite el año. Cuando acepte `?anioAcademico=`, basta con
 * enviarlo entre los filtros y retirar este filtrado y la paginación local.
 */
export function ofAcademicYear(items: Evaluation[], year: string) {
  return items.filter((item) => String(item.anioAcademico) === year);
}

/** Página de una lista ya cargada completa; una página que dejó de existir pasa a la última. */
export function localPage<T>(items: T[], requested: number, size = 20): Page<T> {
  const totalPages = Math.ceil(items.length / size);
  const page = Math.max(0, Math.min(requested, totalPages - 1));
  return {
    content: items.slice(page * size, (page + 1) * size),
    page,
    totalElements: items.length,
    totalPages,
    first: page === 0,
    last: page >= totalPages - 1,
  };
}

/** Años académicos sin repetir, del más reciente al más antiguo. */
export function academicYears(...values: (number | null)[]) {
  return [...new Set(values.filter((value): value is number => value !== null))].sort((a, b) => b - a);
}
