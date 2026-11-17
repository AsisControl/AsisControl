import { api, apiBlob } from "@/core/api/client";
import type { AvailableReport, ProgressReport, ReportContext, ReportPeriod } from "../report-types";

function reportQuery(context: ReportContext) {
  return new URLSearchParams({
    idAlumno: String(context.idAlumno),
    idAsignacionCurso: String(context.idAsignacionCurso),
    idPeriodoAcademico: String(context.idPeriodoAcademico),
  });
}

export function listReportPeriods(assignmentId: number) {
  return api<ReportPeriod[]>(`/api/evaluaciones/periodos-informe?idAsignacionCurso=${assignmentId}`);
}

export function listAvailableReports() {
  return api<AvailableReport[]>("/api/calificaciones/informes-disponibles");
}

export function getProgressReport(context: ReportContext) {
  return api<ProgressReport>(`/api/calificaciones/avance?${reportQuery(context)}`);
}

export function getProgressReportPdf(context: ReportContext) {
  return apiBlob(`/api/calificaciones/reporte-avance?${reportQuery(context)}`, { timeoutMs: 60000 });
}

/** Se invoca solo si el contexto de la descarga sigue seleccionado. */
export function saveProgressReportPdf(blob: Blob, context: ReportContext) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  try {
    anchor.href = url;
    anchor.download = `avance-${context.idAlumno}-${context.idAsignacionCurso}-${context.idPeriodoAcademico}.pdf`;
    document.body.appendChild(anchor);
    anchor.click();
  } finally {
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
