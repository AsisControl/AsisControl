import { api, apiBlob } from "@/core/api/client";
import type { ReportEvidence, ReportRowContext } from "../report-types";

function evidencePath({ idInforme, idDetalleInforme }: ReportRowContext) {
  return `/api/calificaciones/informes/${idInforme}/filas/${idDetalleInforme}/evidencias`;
}

export function getReportRowEvidence(row: ReportRowContext) {
  return api<{ evidencias: ReportEvidence[] }>(evidencePath(row));
}

export function getReportEvidenceAttachment(row: ReportRowContext, evidenceId: number) {
  return apiBlob(`${evidencePath(row)}/${evidenceId}/archivo`, { timeoutMs: 60000 });
}
