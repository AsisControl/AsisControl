"use client";

import { useEffect, useRef, useState } from "react";
import { message } from "@/core/api/client";
import { Feedback } from "@/shared/ui";
import type { AvailableReport, ProgressReport, ReportPeriod } from "./report-types";
import { Empty, ReadPanel } from "./progress-read-panel";
import { ReportContent } from "./report-content";
import { getProgressReport, getProgressReportPdf, listAvailableReports, listReportPeriods, saveProgressReportPdf } from "./services/report-service";
import styles from "./evaluations.module.css";

export function PeriodProgress({ assignmentId }: { assignmentId: number }) {
  const [load] = useState(() => () => listReportPeriods(assignmentId));
  return <ReadPanel load={load}>{(periods) => <PeriodSelector assignmentId={assignmentId} periods={periods} />}</ReadPanel>;
}

function PeriodSelector({ assignmentId, periods }: { assignmentId: number; periods: ReportPeriod[] }) {
  const [id, setId] = useState("");
  const period = periods.find((item) => String(item.id) === id);
  if (!periods.length) return <Empty>No hay períodos académicos disponibles para este curso y sección.</Empty>;
  return <>
    <div className={`panel ${styles.filters}`}>
      <label className="field">Período académico
        <select value={id} onChange={(event) => setId(event.target.value)}>
          <option value="">Selecciona un período académico</option>
          {periods.map((item) => <option key={item.id} value={item.id}>{item.nombre}</option>)}
        </select>
      </label>
    </div>
    {period ? <AuthorizedStudents key={period.id} assignmentId={assignmentId} periodId={period.id} />
      : <Empty>Selecciona un período académico para consultar sus estudiantes autorizados.</Empty>}
  </>;
}

function AuthorizedStudents({ assignmentId, periodId }: { assignmentId: number; periodId: number }) {
  const [load] = useState(() => async () => (await listAvailableReports()).filter((item) =>
    item.idAsignacionCurso === assignmentId && item.idPeriodoAcademico === periodId));
  return <ReadPanel load={load}>{(items) => <ReportStudentSelector items={items} />}</ReadPanel>;
}

function ReportStudentSelector({ items }: { items: AvailableReport[] }) {
  const [id, setId] = useState("");
  const context = items.find((item) => String(item.idAlumno) === id);
  if (!items.length) return <Empty>No hay informes disponibles: aún no hay libretas publicadas para este curso y período. Puedes consultar las evaluaciones en borrador en Consulta por evaluación.</Empty>;
  return <>
    <div className={`panel ${styles.filters}`}>
      <label className="field">Estudiante
        <select value={id} onChange={(event) => setId(event.target.value)}>
          <option value="">Selecciona un estudiante</option>
          {items.map((item) => <option key={item.idAlumno} value={item.idAlumno}>{item.alumno}</option>)}
        </select>
      </label>
    </div>
    {context ? <StudentReport key={context.idAlumno} context={context} /> : <Empty>Selecciona un estudiante para ver su informe del período.</Empty>}
  </>;
}

function StudentReport({ context }: { context: AvailableReport }) {
  const [load] = useState(() => () => getProgressReport(context));
  return <ReadPanel load={load}>{(report) => <ReportWithDownload context={context} report={report} />}</ReadPanel>;
}

function ReportWithDownload({ context, report }: { context: AvailableReport; report: ProgressReport }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const active = useRef(false);
  const downloading = useRef(false);
  useEffect(() => {
    active.current = true;
    return () => { active.current = false; };
  }, []);
  async function download() {
    if (downloading.current) return;
    downloading.current = true;
    setBusy(true);
    setError("");
    try {
      const blob = await getProgressReportPdf(context);
      if (active.current) saveProgressReportPdf(blob);
    } catch (e) {
      if (active.current) setError(message(e));
    } finally {
      downloading.current = false;
      if (active.current) setBusy(false);
    }
  }
  return <>
    <ReportContent report={report} sustento="docente" />
    <div className={`panel ${styles.sheet}`}>
      <Feedback error={error} />
      <button type="button" className="primary-action" disabled={busy} onClick={download}>
        {busy ? <><span className={styles.reportSpinner} aria-hidden="true" /> Generando PDF…</> : error ? "Reintentar descarga PDF" : "Descargar PDF"}
      </button>
      {busy && <span role="status" className={styles.reportSrOnly}>Generando PDF…</span>}
      <p className="hint">El PDF corresponde a la última versión publicada disponible al descargarlo.</p>
    </div>
  </>;
}
