"use client";

import { useState } from "react";
import { dateLabel } from "@/shared/lib/formatters";
import { StatusPill } from "@/shared/ui";
import type { ProgressReport, ProgressReportRow } from "./report-types";
import { ReportEvidenceDialog } from "./report-evidence-dialog";
import styles from "./evaluations.module.css";

export function ReportBadge({ status }: { status: "BORRADOR" | "PUBLICADA" }) {
  return <StatusPill state={status} label={status} tone={status === "BORRADOR" ? "warning" : "success"} />;
}

export function ReportContent({ report, draft = false, sustento }: {
  report: ProgressReport;
  draft?: boolean;
  sustento: "docente" | "publicado";
}) {
  const [selection, setSelection] = useState<{ report: ProgressReport; row: ProgressReportRow } | null>(null);
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
    {!report.filas.length ? <p>No hay resultados disponibles en este informe.</p> : <div className={styles.reportResults}>
      <table>
        <caption>{status ? (status === "BORRADOR" ? "Resultados del borrador" : "Resultados de la libreta publicada")
          : draft ? "Resultados del borrador" : "Resultados de la libreta publicada"}</caption>
        <thead><tr>{["Competencia", "Evaluación", "Fecha", "Resultado", "Ponderación", "Observación"].map((label) => <th key={label} scope="col">{label}</th>)}</tr></thead>
        <tbody>{report.filas.map((row, index) => <tr key={row.idDetalleInforme ?? index}>
          <th scope="row">{row.competencia}</th><td data-label="Evaluación">{row.evaluacion}</td><td data-label="Fecha">{row.fecha}</td>
          <td data-label="Resultado">{/^(AD|A|B|C)$/.test(row.resultado) ? <span className={styles.reportGrade}>{row.resultado}</span> : row.resultado}</td>
          <td data-label="Ponderación">{row.ponderacion}</td>
          <td data-label="Observación" className={styles.reportObservation}>
            {row.observacion}
            {showEvidence && row.idInforme > 0 && row.idDetalleInforme > 0 && <div className={styles.reportEvidenceAction}>
              <button type="button" className="secondary-action" aria-haspopup="dialog"
                aria-label={`Ver sustento de ${row.competencia} en ${row.evaluacion}`}
                onClick={() => setSelection({ report, row })}>Ver sustento</button>
            </div>}
          </td>
        </tr>)}</tbody>
      </table>
    </div>}
    {selectedRow && <ReportEvidenceDialog key={`${selectedRow.idInforme}:${selectedRow.idDetalleInforme}`}
      row={selectedRow} onClose={() => setSelection(null)} />}
  </article>;
}
