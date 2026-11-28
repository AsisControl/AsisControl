"use client";

import { CheckboxSelect } from "@/shared/ui/checkbox-select";
import { useEffect, useRef, useState } from "react";
import { message } from "@/core/api/client";
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
  const [competencies, setCompetencies] = useState<number[] | null>(null);
  const options = [...new Map(report.filas.filter(row => row.idEvaluacion > 0 && row.idCompetencia > 0)
    .map(row => [row.idCompetencia, row.competencia])).entries()];
  const evaluations = groupEvaluations(report.filas.filter(row => competencies === null || competencies.includes(row.idCompetencia)))
    .map(([, [row]]) => row).filter(row => row.idEvaluacion > 0);
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<number[]>([]);
  const [removed, setRemoved] = useState(0);
  const validSelection = selected.filter(id => evaluations.some(row => row.idEvaluacion === id));
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
      const competencyIds = ids ? competencies?.filter(id => report.filas.some(row => row.idCompetencia === id && ids.includes(row.idEvaluacion))) : undefined;
      const pdf = await getProgressReportPdf(context, ids, competencyIds);
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
      {report.filas.some(row => row.idEvaluacion > 0) && <button type="button" className="secondary-action" aria-haspopup="dialog"
        disabled={disabled || !!busy} onClick={() => { setPartialError(""); setOpen(true); }}>
        Descargar evaluaciones seleccionadas
      </button>}
    </div>
    <Feedback error={fullError} />
    {busy && <span className={styles.reportSrOnly} role="status">Generando PDF…</span>}
    {open && <Dialog title="Reporte de evaluaciones seleccionadas" busy={busy === "partial"} onClose={() => setOpen(false)}>
      <p className="hint">Este PDF incluye únicamente las evaluaciones seleccionadas de la libreta publicada.</p>
      <fieldset className={styles.reportPdfSelection} disabled={!!busy}>
        <legend className={styles.reportSrOnly}>Selección del reporte</legend>
        {options.length > 1 && <div><span className={styles.partialFieldLabel}>Competencias</span>
          <CheckboxSelect singular="competencia" plural="competencias"
          options={options.map(([id, label]) => ({ id, label }))}
          selected={competencies ?? options.map(([id]) => id)}
          onChange={ids => {
            setCompetencies(ids.length === options.length ? null : ids);
            const retained = selected.filter(id => report.filas.some(row => row.idEvaluacion === id && ids.includes(row.idCompetencia)));
            setSelected(retained);
            setRemoved(selected.length - retained.length);
          }} /></div>}
        <div><span className={styles.partialFieldLabel}>Evaluaciones</span>
          <CheckboxSelect singular="evaluación" plural="evaluaciones"
          options={evaluations.map(row => ({ id: row.idEvaluacion, label: row.evaluacion + " · " + row.fecha }))}
          selected={validSelection} onChange={ids => { setSelected(ids); setRemoved(0); }} /></div>
      </fieldset>
      {removed > 0 && <p role="status">Se quitaron {removed} evaluaciones que ya no corresponden a las competencias seleccionadas.</p>}
      <Feedback error={partialError} />
      {!validSelection.length && <p className="hint">Selecciona al menos una evaluación para descargar el reporte.</p>}
      <div className="form-actions">
        <button type="button" className="primary-action" disabled={!!busy || !validSelection.length} onClick={() => void download(validSelection)}>
          {busy === "partial" ? "Generando PDF…" : "Descargar reporte seleccionado"}
        </button>
      </div>
    </Dialog>}
  </>;
}
