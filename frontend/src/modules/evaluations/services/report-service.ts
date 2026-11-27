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

function partialEvidenceQuery(context: ReportContext, evaluacion: number, competencia: number) {
  const query = reportQuery(context);
  query.set("idEvaluacion", String(evaluacion));
  query.set("idCompetencia", String(competencia));
  return query;
}

export function getPartialRowEvidence(context: ReportContext, evaluation: number, competency: number) {
  return api<Pick<SustentoInformeDto, "evidencias">>(`/api/calificaciones/informes/parcial/evidencias?${partialEvidenceQuery(context, evaluation, competency)}`);
}

export function getPartialEvidenceAttachment(context: ReportContext, evaluation: number, competency: number, evidenceId: number) {
  return apiBlob(`/api/calificaciones/informes/parcial/evidencias/${evidenceId}/archivo?${partialEvidenceQuery(context, evaluation, competency)}`, { timeoutMs: 60000 });
}

export async function getProgressReportPdf(context: ReportContext, evaluationIds?: number[], competencyIds?: number[]) {
  if (evaluationIds?.length === 0) throw new Error("Selecciona al menos una evaluación para descargar el reporte.");
  const query = reportQuery(context);
  evaluationIds?.forEach((id) => query.append("idEvaluacion", String(id)));
  competencyIds?.forEach((id) => query.append("idCompetencia", String(id)));
  return fetchReportPdf(`/api/calificaciones/reporte-avance?${query}`, Boolean(evaluationIds));
}

async function fetchReportPdf(path: string, partial: boolean) {
  let filename = partial ? "Informe_parcial.pdf" : "Informe_completo.pdf";
  const blob = await apiBlob(path, { timeoutMs: 60000 }, headers => {
    const candidate = headers.get("Content-Disposition")?.match(/filename="((?:Informe_(?:completo|parcial)|(?:Evaluaciones_seleccionadas_)?Avance)_[A-Za-z0-9_]+\.pdf)"/)?.[1];
    if (candidate) filename = candidate;
  });
  return { blob, filename };
}

/** Se invoca solo si el contexto de la descarga sigue seleccionado. */
export function saveProgressReportPdf({ blob, filename }: Awaited<ReturnType<typeof getProgressReportPdf>>) {
  saveBlob(blob, filename);
}

export type ReportPeriodStatus = {
  alumnos: { idAlumno: number; alumno: string; borrador: boolean; publicada: boolean; revisado?: boolean }[];
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

export function confirmReportReview(context: ReportContext, version: number) {
  return api<ProgressReport>(`/api/calificaciones/informes/borrador/revision?${reportQuery(context)}`, {
    method: "PUT", body: JSON.stringify({ version }),
  });
}

export type PartialEvaluation = { id: number; nombre: string; tipo: string; fecha: string; competencias?: { id: number; nombre: string }[] };

export function listPartialEvaluations(assignment: number, period: number) {
  return api<PartialEvaluation[]>(`/api/calificaciones/informes/parcial/evaluaciones?idAsignacionCurso=${assignment}&idPeriodoAcademico=${period}`);
}

function partialQuery(context: ReportContext, ids: number[], competencias: number[] = []) {
  const query = reportQuery(context);
  ids.forEach(id => query.append("idEvaluacion", String(id)));
  competencias.forEach(id => query.append("idCompetencia", String(id)));
  return query;
}

export function getPartialReport(context: ReportContext, ids: number[], competencias: number[] = []) {
  return api<ProgressReport>(`/api/calificaciones/informes/parcial?${partialQuery(context, ids, competencias)}`);
}

export async function downloadPartialReport(context: ReportContext, ids: number[], competencias: number[] = []) {
  saveProgressReportPdf(await fetchReportPdf(`/api/calificaciones/informes/parcial/pdf?${partialQuery(context, ids, competencias)}`, true));
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
