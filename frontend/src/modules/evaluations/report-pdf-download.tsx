"use client";

import { useEffect, useRef, useState } from "react";
import { message } from "@/core/api/client";
import { countLabel } from "@/shared/lib/formatters";
import { Dialog, Feedback } from "@/shared/ui";
import { groupEvaluations } from "./report-content";
import type { ProgressReport, ReportContext } from "./report-types";
import { getProgressReportPdf, saveProgressReportPdf } from "./services/report-service";
import styles from "./evaluations.module.css";

export function ReportPdfDownload({ context, report, showFull = true, disabled = false }: {
  context: ReportContext;
  report: ProgressReport;
  showFull?: boolean;
  disabled?: boolean;
}) {
  const evaluations = groupEvaluations(report.filas).map(([, [row]]) => row).filter((row) => row.idEvaluacion > 0);
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<number[]>([]);
  const [fullError, setFullError] = useState("");
  const [partialError, setPartialError] = useState("");
  const [busy, setBusy] = useState<"full" | "partial" | null>(null);
  const active = useRef(false);
  const downloading = useRef(false);
  useEffect(() => {
    active.current = true;
    return () => { active.current = false; };
  }, []);

  async function download(ids?: number[]) {
    if (disabled || downloading.current || ids?.length === 0) return;
    const setError = ids ? setPartialError : setFullError;
    setError("");
    downloading.current = true;
    setBusy(ids ? "partial" : "full");
    try {
      const pdf = await getProgressReportPdf(context, ids);
      if (active.current) saveProgressReportPdf(pdf);
    } catch (failure) {
      if (active.current) setError(message(failure));
    } finally {
      downloading.current = false;
      if (active.current) setBusy(null);
    }
  }

  return <>
    <div className={styles.reportActions}>
      {showFull && <button type="button" className="primary-action" disabled={disabled || !!busy} onClick={() => void download()}>
        {busy === "full" ? "Generando PDF…" : fullError ? "Reintentar descarga PDF" : "Descargar libreta completa"}
      </button>}
      {evaluations.length > 0 && <button type="button" className="secondary-action" aria-haspopup="dialog"
        disabled={disabled || !!busy} onClick={() => { setPartialError(""); setOpen(true); }}>
        Descargar evaluaciones seleccionadas
      </button>}
    </div>
    <Feedback error={fullError} />
    {busy && <span className={styles.reportSrOnly} role="status">Generando PDF…</span>}
    {open && <Dialog title="Reporte de evaluaciones seleccionadas" busy={busy === "partial"} onClose={() => setOpen(false)}>
      <p className="hint">Este PDF incluye únicamente las evaluaciones seleccionadas de la libreta publicada.</p>
      <fieldset className={styles.reportPdfSelection} disabled={!!busy}>
        <legend className={styles.reportSrOnly}>Evaluaciones de la libreta</legend>
        <div className={styles.reportActions}>
          <span aria-live="polite">{countLabel(selected.length, "evaluación seleccionada", "evaluaciones seleccionadas")}</span>
          <button type="button" className="secondary-action" onClick={() => setSelected(evaluations.map((row) => row.idEvaluacion))}>Seleccionar todas</button>
          <button type="button" className="secondary-action" disabled={!selected.length} onClick={() => setSelected([])}>Limpiar selección</button>
        </div>
        <div className={styles.reportPdfList}>
          {evaluations.map((row) => <label key={row.idEvaluacion}>
            <input type="checkbox" checked={selected.includes(row.idEvaluacion)} onChange={() => setSelected((current) =>
              current.includes(row.idEvaluacion) ? current.filter((id) => id !== row.idEvaluacion) : [...current, row.idEvaluacion])} />
            <span>{row.evaluacion} · {row.fecha}</span>
          </label>)}
        </div>
      </fieldset>
      <Feedback error={partialError} />
      {!selected.length && <p className="hint">Selecciona al menos una evaluación para descargar el reporte.</p>}
      <div className="form-actions">
        <button type="button" disabled={!!busy} onClick={() => setOpen(false)}>Cerrar</button>
        <button type="button" className="primary-action" disabled={!!busy || !selected.length} onClick={() => void download(selected)}>
          {busy === "partial" ? "Generando PDF…" : "Descargar reporte seleccionado"}
        </button>
      </div>
    </Dialog>}
  </>;
}
