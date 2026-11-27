"use client";

import { useEffect, useRef, useState } from "react";
import { ApiError, message } from "@/core/api/client";
import { saveBlob } from "@/shared/lib/save-blob";
import { Dialog, Feedback, Loading } from "@/shared/ui";
import type { ProgressReportRow, ReportEvidence, ReportContext } from "./report-types";
import { getReportEvidenceAttachment, getReportRowEvidence, getPartialEvidenceAttachment, getPartialRowEvidence } from "./services/report-service";
import { EvidenceFileCard } from "./evidence-file-card";
import styles from "./evaluations.module.css";

function evidenceError(error: unknown, action: "consultar" | "descargar") {
  if (error instanceof ApiError && error.status === 403) {
    return `No tienes permiso para ${action} este sustento.`;
  }
  if (error instanceof ApiError && error.status === 404) {
    return action === "descargar" ? "El archivo ya no está disponible." : "La fila de la libreta ya no está disponible.";
  }
  return `No se pudo ${action} el sustento. ${message(error)}`;
}

export function ReportEvidenceDialog({ row, partialContext, onClose }: { row: ProgressReportRow; partialContext?: ReportContext; onClose: () => void }) {
  const [result, setResult] = useState<{ evidence: ReportEvidence[] } | { error: string } | null>(null);
  const [downloadError, setDownloadError] = useState("");
  const [downloading, setDownloading] = useState<number | null>(null);
  const active = useRef(false);

  useEffect(() => {
    let current = true;
    active.current = true;
    (partialContext ? getPartialRowEvidence(partialContext, row.idEvaluacion, row.idCompetencia)
      : getReportRowEvidence(row)).then(
      response => { if (current) setResult({ evidence: response.evidencias }); },
      error => { if (current) setResult({ error: evidenceError(error, "consultar") }); },
    );
    return () => { current = false; active.current = false; };
  }, [row, partialContext]);

  async function download(evidence: ReportEvidence) {
    if (!evidence.archivo || downloading !== null) return;
    const filename = evidence.archivo.nombreOriginal;
    setDownloading(evidence.idEvidencia);
    setDownloadError("");
    try {
      const file = await (partialContext ? getPartialEvidenceAttachment(partialContext, row.idEvaluacion, row.idCompetencia, evidence.idEvidencia)
        : getReportEvidenceAttachment(row, evidence.idEvidencia));
      if (active.current) saveBlob(file, filename);
    } catch (error) {
      if (active.current) setDownloadError(evidenceError(error, "descargar"));
    } finally {
      if (active.current) setDownloading(null);
    }
  }

  return <Dialog title="Sustento de la competencia" description={<>
    <strong>{row.competencia}</strong><br />
    {row.evaluacion}{row.fecha && <> · {row.fecha}</>}
  </>} onClose={onClose}>
    {!result && <Loading variant="inline">Consultando sustento…</Loading>}
    {result && ("error" in result ? <Feedback error={result.error} /> : result.evidence.length
      ? <ul className={styles.reportEvidenceList}>
          {result.evidence.map(evidence => <li key={evidence.idEvidencia}>
            <h4>Descripción de la evidencia</h4>
            <p className={!evidence.descripcion ? "hint" : undefined}>{evidence.descripcion || "Sin descripción registrada."}</p>
            {evidence.archivo ? <EvidenceFileCard name={evidence.archivo.nombreOriginal}
              mime={evidence.archivo.tipoMime} bytes={evidence.archivo.tamanioBytes}>
              <button type="button" className="secondary-action"
                aria-label={`Descargar ${evidence.archivo.nombreOriginal}`}
                disabled={downloading !== null} onClick={() => void download(evidence)}>
                {downloading === evidence.idEvidencia ? "Descargando…" : "Descargar archivo"}
              </button>
            </EvidenceFileCard> : <p className="hint">Esta evidencia no tiene archivo adjunto.</p>}
          </li>)}
        </ul>
      : <p>No hay sustento disponible para esta competencia.</p>)}
    <Feedback error={downloadError} />
  </Dialog>;
}
