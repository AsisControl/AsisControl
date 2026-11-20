import { api, apiBlob } from "@/core/api/client";
import { saveBlob } from "@/shared/lib/save-blob";
import type { AvailableReport, ProgressReport, ReportContext, ReportPeriod, ReportRowContext, SustentoInformeDto } from "../report-types";

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

function evidencePath({ idInforme, idDetalleInforme }: ReportRowContext) {
  return `/api/calificaciones/informes/${idInforme}/filas/${idDetalleInforme}/evidencias`;
}

export function getReportRowEvidence(row: ReportRowContext) {
  return api<SustentoInformeDto>(evidencePath(row));
}

export function getReportEvidenceAttachment(row: ReportRowContext, evidenceId: number) {
  return apiBlob(`${evidencePath(row)}/${evidenceId}/archivo`, { timeoutMs: 60000 });
}

export async function getProgressReportPdf(context: ReportContext) {
  let filename = "Avance.pdf";
  const blob = await apiBlob(`/api/calificaciones/reporte-avance?${reportQuery(context)}`, { timeoutMs: 60000 }, headers => {
    const candidate = headers.get("Content-Disposition")?.match(/filename="(Avance_[A-Za-z0-9_]+_V[0-9]+\.pdf)"/)?.[1];
    if (candidate) filename = candidate;
  });
  return { blob, filename };
}

/** Se invoca solo si el contexto de la descarga sigue seleccionado. */
export function saveProgressReportPdf({ blob, filename }: Awaited<ReturnType<typeof getProgressReportPdf>>) {
  saveBlob(blob, filename);
}

export type ReportPeriodStatus = {
  alumnos: { idAlumno: number; alumno: string; borrador: boolean; publicada: boolean }[];
  publicable: boolean;
  desactualizada: boolean;
  impedimento: string | null;
};

export function getReportPeriodStatus(idAsignacionCurso: number, idPeriodoAcademico: number) {
  const query = new URLSearchParams({ idAsignacionCurso: String(idAsignacionCurso), idPeriodoAcademico: String(idPeriodoAcademico) });
  return api<ReportPeriodStatus>(`/api/calificaciones/informes/estado?${query}`);
}

export function getProgressReportDraft(context: ReportContext) {
  return api<ProgressReport>(`/api/calificaciones/informes/borrador?${reportQuery(context)}`);
}

function periodReportRequest(idAsignacionCurso: number, idPeriodoAcademico: number) {
  return {
    method: "POST",
    body: JSON.stringify({ idAsignacionCurso, idPeriodoAcademico }),
    timeoutMs: 60000,
  };
}

export function generateReportDrafts(assignmentId: number, periodId: number) {
  return api<{ informesProcesados: number; informes: AvailableReport[] }>(
    "/api/calificaciones/informes/generar", periodReportRequest(assignmentId, periodId),
  );
}

export function publishPeriodReports(assignmentId: number, periodId: number) {
  return api<{ informesProcesados: number }>(
    "/api/calificaciones/informes/publicar", periodReportRequest(assignmentId, periodId),
  );
}
