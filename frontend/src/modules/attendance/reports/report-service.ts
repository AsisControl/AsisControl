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

// correccion marcacion docente
export type TeacherMarking = { id: number; fechaJornada: string; fechaHoraMarcada: string; estado: string; version: number };
export type TeacherMarkingCorrection = {
  id: number; horaAnterior: string; horaNueva: string; estadoAnterior: string; estadoNuevo: string;
  motivo: string; actor: string; fechaHora: string;
};
export type TeacherMarkingHistory = { marcacion: TeacherMarking; correcciones: TeacherMarkingCorrection[] };
const MARKINGS_PATH = "/api/asistencias/docentes";
export const teacherMarkingHistory = (id: number) => api<TeacherMarkingHistory>(`${MARKINGS_PATH}/${id}/correcciones`);
export const correctTeacherMarking = (id: number, body: { hora: string | null; estado: string | null; motivo: string; version: number }) =>
  api<TeacherMarking>(`${MARKINGS_PATH}/${id}/correccion`, { method: "PUT", body: JSON.stringify(body) });
