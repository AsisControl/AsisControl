"use client";

import { useState } from "react";
import { countLabel, dateLabel } from "@/shared/lib/formatters";
import { StatusPill } from "@/shared/ui";
import type { ProgressReport, ProgressReportRow } from "./report-types";
import { ReportEvidenceDialog } from "./report-evidence-dialog";
import { STATE_TONES } from "./lib/labels";
import styles from "./evaluations.module.css";

const EVALUATIONS_PER_PAGE = 5;
const EVALUATION_TYPES: Record<string, string> = {
  EXAMEN: "Examen",
  PRACTICA_CALIFICADA: "Práctica calificada",
  EVALUACION_ORAL: "Evaluación oral",
  PROYECTO: "Proyecto",
  TRABAJO_ACADEMICO: "Trabajo académico",
};
const EVALUATION_STATES = { BORRADOR: "Borrador", PUBLICADA: "Publicada", CERRADA: "Cerrada", ANULADA: "Anulada" };

export function groupEvaluations(rows: ProgressReportRow[]) {
  const groups = new Map<string, ProgressReportRow[]>();
  for (const row of rows) {
    const key = row.idEvaluacion ? String(row.idEvaluacion) : `${row.evaluacion}:${row.fecha}`;
    const group = groups.get(key);
    if (group) group.push(row);
    else groups.set(key, [row]);
  }
  const dateKey = (row: ProgressReportRow) => row.fecha.split("/").reverse().join("");
  return Array.from(groups).sort(([, [a]], [, [b]]) => dateKey(a).localeCompare(dateKey(b)));
}

export function ReportBadge({ status }: { status: "BORRADOR" | "PUBLICADA" }) {
  return <StatusPill state={status} label={status} tone={status === "BORRADOR" ? "warning" : "success"} />;
}

export function ReportContent({ report, draft = false, sustento }: {
  report: ProgressReport;
  draft?: boolean;
  sustento: "docente" | "publicado";
}) {
  const [selection, setSelection] = useState<{ report: ProgressReport; row: ProgressReportRow } | null>(null);
  const [view, setView] = useState({ report, page: 0, type: "" });
  const { page, type } = view.report === report ? view : { page: 0, type: "" };
  const evaluations = groupEvaluations(report.filas);
  const types = [...new Set(evaluations.flatMap(([, [row]]) => row.tipoEvaluacion ? [row.tipoEvaluacion] : []))];
  const filtered = type ? evaluations.filter(([, [row]]) => row.tipoEvaluacion === type) : evaluations;
  const pages = Math.ceil(filtered.length / EVALUATIONS_PER_PAGE);
  const pageStart = page * EVALUATIONS_PER_PAGE;
  const visible = filtered.slice(pageStart, pageStart + EVALUATIONS_PER_PAGE);
  const evaluationCount = countLabel(evaluations.length, "evaluación", "evaluaciones");
  const version = report.periodo.match(/^(.*) \| Version (\d+) \| (BORRADOR|PUBLICADA)$/);
  const status = version?.[3] === "BORRADOR" ? "BORRADOR" : version?.[3] === "PUBLICADA" ? "PUBLICADA" : undefined;
  const showEvidence = sustento === "docente" || status === "PUBLICADA";
  const selectedRow = showEvidence && selection?.report === report ? selection.row : null;
  if (sustento === "publicado" && status === "BORRADOR") {
    return <p className={styles.reportNotice}>No hay una libreta publicada para este período y curso.</p>;
  }
  return <article className={`panel ${styles.reportDocument}`} aria-label="Informe de progreso por período">
    <header className={styles.reportCover}>
      <h2>{report.alumno}</h2>
      <div className={styles.reportMeta}>
        <span>Código: {report.codigoAlumno || "Sin código de estudiante"}</span>
        <span>{report.curso} · {report.grado}° {report.seccion} · {report.anio}</span>
      </div>
      {status && <div className={styles.reportActions}><ReportBadge status={status} /><span>Versión {version?.[2]}</span></div>}
      <p>{version?.[1] ?? report.periodo} · {report.fechas}</p>
      <div className={styles.reportMeta}>
        <span>Docente: {report.docente}</span>
        <span>Generado: {dateLabel(report.generadoEn)} (Lima)</span>
      </div>
    </header>
    <dl className={styles.reportLegend} aria-label="Escala de logro">
      {[["AD", "Logro destacado"], ["A", "Logro esperado"], ["B", "En proceso"], ["C", "En inicio"]].map(([level, label]) =>
        <div key={level}><dt>{level}</dt><dd>{label}</dd></div>)}
    </dl>
    {report.reglaCalificacion && <section aria-label="Consolidado por competencia">
      <h3>Consolidado por competencia</h3>
      <p>Regla de calificación · versión {report.reglaCalificacion.numeroVersion}</p>
      <p>La columna Ponderación muestra el aporte por evaluación para cada competencia en esta versión.</p>
      <ul>{report.consolidado?.map((item) => <li key={item.idCompetencia}>
        {item.nombreCompetencia}: {item.nivelConsolidado ?? "Datos insuficientes"}
        {item.componentesFaltantes.length > 0 && ` · Faltan: ${item.componentesFaltantes.join(", ")}`}
      </li>)}</ul>
    </section>}
    {!evaluations.length ? <p>No hay resultados disponibles en este informe.</p> : <section className={styles.reportResults}
      aria-label={status === "BORRADOR" || (!status && draft) ? "Resultados del borrador" : "Resultados de la libreta publicada"}>
      {types.length > 1 && <label className={`field ${styles.reportTypeFilter}`}>
        Tipo de evaluación
        <select value={type} onChange={(event) => setView({ report, page: 0, type: event.target.value })}>
          <option value="">Todos los tipos</option>
          {types.map((item) => <option key={item} value={item}>{EVALUATION_TYPES[item] ?? item}</option>)}
        </select>
      </label>}
      <p className={styles.reportGroupCount} aria-live="polite">
        {filtered.length < evaluations.length ? `${filtered.length} de ${evaluationCount}` : evaluationCount}
      </p>
      <div key={`${type}:${page}`} className={styles.reportGroups}>
        {visible.map(([key, rows], index) => {
          const [evaluation] = rows;
          const state = evaluation.estadoEvaluacion;
          return <details key={`${evaluation.idInforme}:${key}`} className={styles.reportGroup} open={index === 0}>
            <summary className={styles.reportGroupSummary}>
              <span className={styles.reportGroupNumber}>{pageStart + index + 1}.</span>
              <strong>{evaluation.evaluacion}</strong>
              <span className={styles.reportGroupMeta}>
                <span>{evaluation.fecha}</span>
                {state && <StatusPill state={state} label={EVALUATION_STATES[state]} tone={STATE_TONES[state]} />}
              </span>
              <span aria-hidden="true" className={styles.reportChevron} />
            </summary>
            <div className={styles.reportGroupContent}>
              <table aria-label={`Competencias de ${evaluation.evaluacion}`}>
                <thead><tr>{["Competencia", "Resultado", "Ponderación", "Observación", "Sustento"].map((label) => <th key={label} scope="col">{label}</th>)}</tr></thead>
                <tbody>{rows.map((row, rowIndex) => <tr key={row.idDetalleInforme ?? rowIndex}>
                  <th scope="row">{row.competencia}</th>
                  <td data-label="Resultado">{/^(AD|A|B|C)$/.test(row.resultado) ? <span className={styles.reportGrade}>{row.resultado}</span> : row.resultado}</td>
                  <td data-label="Ponderación">{row.ponderacion}</td>
                  <td data-label="Observación" className={styles.reportObservation}>{row.observacion}</td>
                  <td data-label="Sustento" className={styles.reportEvidenceAction}>
                    {showEvidence && row.idInforme > 0 && row.idDetalleInforme > 0 ? <button type="button" className="secondary-action" aria-haspopup="dialog"
                      aria-label={`Ver sustento de ${row.competencia} en ${row.evaluacion}`}
                      onClick={() => setSelection({ report, row })}>Ver sustento</button> : "—"}
                  </td>
                </tr>)}</tbody>
              </table>
            </div>
          </details>;
        })}
      </div>
      {pages > 1 && <nav className="pager" aria-label="Paginación de evaluaciones">
        <span>Página {page + 1} de {pages}</span>
        <div>
          <button type="button" className="secondary-action" disabled={page === 0} onClick={() => setView({ report, page: page - 1, type })}>Anterior</button>
          <button type="button" className="secondary-action" disabled={page + 1 === pages} onClick={() => setView({ report, page: page + 1, type })}>Siguiente</button>
        </div>
      </nav>}
    </section>}
    {selectedRow && <ReportEvidenceDialog key={`${selectedRow.idInforme}:${selectedRow.idDetalleInforme}`}
      row={selectedRow} onClose={() => setSelection(null)} />}
  </article>;
}
