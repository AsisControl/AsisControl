import { api } from "@/core/api/client";
import { normalizePage } from "@/shared/lib/pagination";
import type { Page } from "@/shared/types/api";
import type { AchievementLevel, Evaluation } from "../types";
import type { ProgressReport } from "../report-types";

const ACADEMIC_REPORTS_PATH = "/api/calificaciones/informes/academicos";

export type AcademicReport = {
  id: number;
  idAlumno: number;
  alumno: string;
  codigoAlumno: string | null;
  estado: "PUBLICADA" | "BORRADOR";
  numeroVersion: number;
  revisado: boolean;
  logros: Partial<Record<AchievementLevel, number>>;
  pendientes: number;
  noEvaluadas: number;
};

export type AcademicReportSummary = {
  estudiantes: { id: number; nombre: string; codigoAlumno: string | null }[];
  libretas: AcademicReport[];
};

export function obtenerResumenAcademico(query: string) {
  return api<AcademicReportSummary>(`${ACADEMIC_REPORTS_PATH}/resumen?${query}`);
}

export function obtenerLibretaAcademica(id: number) {
  return api<ProgressReport>(`${ACADEMIC_REPORTS_PATH}/${id}`);
}

export type ProgressGrade = {
  id: number;
  idEvaluacion: number;
  idAlumno: number;
  idCompetencia: number;
  nivelLogro: AchievementLevel;
  observacion: string | null;
};

/** Consume todas las páginas para no presentar una consulta incompleta como vacía. */
async function readPages<T>(path: string): Promise<T[]> {
  const items: T[] = [];
  for (let number = 0; ; number++) {
    const page = normalizePage(await api<Page<T>>(`${path}&size=100&page=${number}`));
    items.push(...page.content);
    if (page.last) return items;
  }
}

export async function listProgressEvaluations(assignmentId: number) {
  const items = await readPages<Evaluation>(`/api/evaluaciones?idAsignacionCurso=${assignmentId}`);
  return items.filter((item) => item.idAsignacionCurso === assignmentId && item.estado !== "ANULADA");
}

export async function listStudentGrades(evaluationId: number, studentId: number) {
  const items = await readPages<ProgressGrade>(
    `/api/calificaciones?idEvaluacion=${evaluationId}&idAlumno=${studentId}`,
  );
  return items.filter((item) => item.idEvaluacion === evaluationId && item.idAlumno === studentId);
}
