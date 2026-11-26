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
  const [competencies, setCompetencies] = useState<number[] | null>(null);
  const [competencySearch, setCompetencySearch] = useState("");
  const options = [...new Map(report.filas.filter(row => row.idEvaluacion > 0 && row.idCompetencia > 0)
    .map(row => [row.idCompetencia, row.competencia])).entries()];
  const evaluations = groupEvaluations(report.filas.filter(row => competencies === null || competencies.includes(row.idCompetencia)))
    .map(([, [row]]) => row).filter(row => row.idEvaluacion > 0);
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState("");
  const [selected, setSelected] = useState<number[]>([]);
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
      {options.length > 1 && <fieldset className={styles.reportPdfSelection} disabled={!!busy}>
        <legend>Competencias</legend>
        <label className="field">Buscar competencias
          <input type="search" value={competencySearch} onChange={event => setCompetencySearch(event.target.value)} />
        </label>
        <div className={styles.reportActions}>
          <button type="button" className="secondary-action" onClick={() => setCompetencies(null)}>Todas las competencias</button>
          <button type="button" className="secondary-action" onClick={() => setCompetencies([])}>Limpiar competencias</button>
          <span>{competencies?.length ?? options.length} de {options.length}</span>
        </div>
        <div className={styles.reportPdfList}>{options.filter(([, name]) =>
          name.toLocaleLowerCase("es").includes(competencySearch.trim().toLocaleLowerCase("es"))).map(([id, name]) => <label key={id}>
          <input type="checkbox" checked={competencies === null || competencies.includes(id)} onChange={event => {
            const current = competencies ?? options.map(([option]) => option);
            setCompetencies(event.target.checked ? [...current, id] : current.filter(item => item !== id));
          }} />{name}
        </label>)}</div>
      </fieldset>}
      <label className="field">Filtrar evaluaciones
        <input type="search" value={filter} onChange={event => setFilter(event.target.value)} />
      </label>
      <fieldset className={styles.reportPdfSelection} disabled={!!busy}>
        <legend className={styles.reportSrOnly}>Evaluaciones de la libreta</legend>
        <div className={styles.reportActions}>
          <span aria-live="polite">{countLabel(validSelection.length, "evaluación seleccionada", "evaluaciones seleccionadas")}</span>
          <button type="button" className="secondary-action" onClick={() => setSelected(evaluations.map((row) => row.idEvaluacion))}>Seleccionar todas</button>
          <button type="button" className="secondary-action" disabled={!selected.length} onClick={() => setSelected([])}>Limpiar selección</button>
        </div>
        <div className={styles.reportPdfList}>
          {evaluations.filter(row => row.evaluacion.toLocaleLowerCase("es").includes(filter.trim().toLocaleLowerCase("es")))
            .map((row) => <label key={row.idEvaluacion}>
            <input type="checkbox" checked={selected.includes(row.idEvaluacion)} onChange={() => setSelected((current) =>
              current.includes(row.idEvaluacion) ? current.filter((id) => id !== row.idEvaluacion) : [...current, row.idEvaluacion])} />
            <span>{row.evaluacion} · {row.fecha}</span>
          </label>)}
        </div>
      </fieldset>
      <Feedback error={partialError} />
      {!validSelection.length && <p className="hint">Selecciona al menos una evaluación para descargar el reporte.</p>}
      <div className="form-actions">
        <button type="button" disabled={!!busy} onClick={() => setOpen(false)}>Cerrar</button>
        <button type="button" className="primary-action" disabled={!!busy || !validSelection.length} onClick={() => void download(validSelection)}>
          {busy === "partial" ? "Generando PDF…" : "Descargar reporte seleccionado"}
        </button>
      </div>
    </Dialog>}
  </>;
}
