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

export function ReportContent({ report, draft = false, partial = false, sustento }: {
  report: ProgressReport;
  draft?: boolean;
  partial?: boolean;
  sustento: "docente" | "publicado";
}) {
  const [selection, setSelection] = useState<{ report: ProgressReport; row: ProgressReportRow } | null>(null);
  const [view, setView] = useState({ report, page: 0, type: "" });
  const [evaluationReport, setEvaluationReport] = useState<ProgressReport | null>(null);
  const evaluationView = evaluationReport === report;
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
  const hasEffectiveWeight = Boolean(report.reglaCalificacion || report.consolidado?.length);
  const weightHeading = hasEffectiveWeight ? (status === "BORRADOR" || draft ? "Peso efectivo provisional" : "Peso efectivo") : "Ponderación";
  const scaleVersions = [...new Set(report.consolidado?.flatMap(item => item.numeroVersionEscala == null
    ? [] : [item.numeroVersionEscala]))];
  const groups = new Map<string, { name: string; rows: ProgressReportRow[]; summary?: NonNullable<ProgressReport["consolidado"]>[number] }>();
  if (!partial) for (const summary of report.consolidado ?? []) {
    groups.set(String(summary.idCompetencia), { name: summary.nombreCompetencia, rows: [], summary });
  }
  for (const row of report.filas) {
    const key = row.idCompetencia > 0 ? String(row.idCompetencia) : row.competencia;
    const group = groups.get(key);
    if (group) group.rows.push(row);
    else groups.set(key, { name: row.competencia, rows: [row] });
  }

  function resultTable(rows: ProgressReportRow[], groupedByCompetency: boolean, title: string) {
    return <div className={styles.reportGroupContent}>
      <table aria-label={title}>
        <thead><tr>{[...(groupedByCompetency ? ["Evaluación", "Fecha"] : ["Competencia"]), "Resultado", weightHeading, "Observación", "Sustento"]
          .map(label => <th key={label} scope="col" className={label === "Resultado" || label === weightHeading ? styles.reportNumeric : undefined}>{label}</th>)}</tr></thead>
        <tbody>{rows.map(row => <tr key={row.idDetalleInforme > 0 ? row.idDetalleInforme
          : `${row.idEvaluacion || row.evaluacion}:${row.idCompetencia || row.competencia}`}>
          <th scope="row">{groupedByCompetency ? <>{row.evaluacion}
            {row.tipoEvaluacion && <small className="hint"> · {EVALUATION_TYPES[row.tipoEvaluacion] ?? row.tipoEvaluacion}</small>}</> : row.competencia}</th>
          {groupedByCompetency && <td data-label="Fecha">{row.fecha}</td>}
          <td data-label="Resultado" className={styles.reportNumeric}>{/^(AD|A|B|C)$/.test(row.resultado) ? <span className={styles.reportGrade}>{row.resultado}</span> : row.resultado}</td>
          <td data-label={weightHeading} className={styles.reportNumeric}>{row.ponderacion}</td>
          <td data-label="Observación" className={styles.reportObservation}>{row.observacion || "Sin observaciones"}</td>
          <td data-label="Sustento" className={styles.reportEvidenceAction}>
            {showEvidence && row.tieneSustento === true && row.idInforme > 0 && row.idDetalleInforme > 0
              ? <button type="button" className="secondary-action" aria-haspopup="dialog"
                aria-label={`Ver sustento de ${row.competencia} en ${row.evaluacion}`}
                onClick={() => setSelection({ report, row })}>Ver sustento</button>
              : <span className="hint">{!partial && row.tieneSustento === false ? "Sin sustento registrado" : "Sustento no disponible"}</span>}
          </td>
        </tr>)}</tbody>
      </table>
    </div>;
  }

  return <article className={`panel ${styles.reportDocument}`} aria-label="Informe de progreso por período">
    <header className={styles.reportCover}>
      {partial && <p>Informe parcial de progreso</p>}
      {partial && (report.reglaCalificacion || report.filas.some(row => row.numeroVersionEscala != null)) &&
        <div className={styles.consolidationVersions}>
          {report.reglaCalificacion && <span>Regla v{report.reglaCalificacion.numeroVersion}</span>}
          {[...new Set(report.filas.flatMap(row => row.numeroVersionEscala == null ? [] : [row.numeroVersionEscala]))]
            .map(scale => <span key={scale}>Escala institucional v{scale}</span>)}
        </div>}
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
    {!partial && (report.reglaCalificacion || scaleVersions.length > 0) && <div className={styles.consolidationVersions}>
      {report.reglaCalificacion && <span>Regla v{report.reglaCalificacion.numeroVersion}</span>}
      {scaleVersions.map(version => <span key={version}>Escala institucional v{version}</span>)}
    </div>}
    {!partial && !!evaluations.length && <button type="button" className="secondary-action"
      onClick={() => setEvaluationReport(evaluationView ? null : report)}>{evaluationView ? "Ver por competencia" : "Ver por evaluación"}</button>}
    {(partial || !evaluationView) && (groups.size ? <section className={styles.reportResults}
      aria-label={partial ? "Resultados seleccionados" : "Consolidado por competencia"}>
      <div className={styles.reportCompetencies}>{[...groups].map(([key, { name, rows, summary }], index) =>
        <section className={styles.reportCompetency} key={key} aria-label={name}>
          <header><h3>{name}</h3>
            {summary && <StatusPill state={summary.nivelConsolidado ?? summary.estadoAvance ?? "PENDIENTE"}
              label={summary.nivelConsolidado ?? (summary.estadoAvance === "NO_EVALUADA" ? "Aún no evaluada" : "Pendiente")}
              tone={summary.nivelConsolidado ? "info" : summary.estadoAvance === "NO_EVALUADA" ? "neutral" : "warning"} />}
          </header>
          {!!summary?.componentesFaltantes.length && <p className="hint">
            {summary.estadoAvance ? summary.componentesFaltantes.join(" · ") : `Faltan resultados de ${summary.componentesFaltantes.join(" · ")}`}
          </p>}
          {status === "PUBLICADA" && summary && report.conclusionesDescriptivas?.[summary.idCompetencia] && <p className={styles.conclusionText}>
            <strong>Conclusión descriptiva histórica</strong> {report.conclusionesDescriptivas[summary.idCompetencia]}
          </p>}
          {!!rows.length && <details className={styles.resultDetails} open={partial && index === 0}>
            <summary>Ver detalle del resultado<span className={styles.srOnly}> de {name}</span></summary>
            {resultTable(rows, true, `Evaluaciones de ${name}`)}
          </details>}
        </section>)}</div>
    </section> : <p>No hay resultados disponibles en este informe.</p>)}
    {!partial && evaluationView && !!evaluations.length && <section className={styles.reportResults}
      aria-label={partial ? "Resultados seleccionados" : status === "BORRADOR" || (!status && draft) ? "Resultados del borrador" : "Resultados de la libreta publicada"}>
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
            {resultTable(rows, false, `Competencias de ${evaluation.evaluacion}`)}
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
