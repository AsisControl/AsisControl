import { api } from "@/core/api/client";

export type ReportType = "ESTUDIANTES" | "DOCENTES";
export type ReportState = "PRESENTE" | "TARDANZA" | "INASISTENCIA" | "SIN_MARCACION";
export type ReportRow = {
  id: number; personaId: number; persona: string; fecha: string; hora: string | null;
  curso: string | null; seccion: string | null; estado: string; origen: string; sesionId: number | null;
};
export type Report = {
  total: number; indicadores: { estado: ReportState; cantidad: number; porcentaje: number }[];
  registros: ReportRow[]; pagina: number; totalPaginas: number; totalDetalle: number;
};
export type Options = { periodos: { id: number; nombre: string }[]; secciones: { id: number; nombre: string }[] };
export const REPORT_PATH = "/api/reportes/asistencia";
export const reportOptions = () => api<Options>(`${REPORT_PATH}/filtros`);
export const attendanceReport = (query: string) => api<Report>(`${REPORT_PATH}?${query}`);
