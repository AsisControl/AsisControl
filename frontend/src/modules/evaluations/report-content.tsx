import { dateLabel } from "@/shared/lib/formatters";
import { StatusPill } from "@/shared/ui";
import type { ProgressReport } from "./report-types";
import styles from "./evaluations.module.css";

export function ReportBadge({ status }: { status: "BORRADOR" | "PUBLICADA" }) {
  return <StatusPill state={status} label={status} tone={status === "BORRADOR" ? "warning" : "success"} />;
}

export function ReportContent({ report, draft = false }: { report: ProgressReport; draft?: boolean }) {
  const version = report.periodo.match(/^(.*) \| Version (\d+) \| (BORRADOR|PUBLICADA)$/);
  const status = version?.[3] === "BORRADOR" ? "BORRADOR" : version?.[3] === "PUBLICADA" ? "PUBLICADA" : undefined;
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
    {!report.filas.length ? <p>No hay resultados disponibles en este informe.</p> : <div className={styles.reportResults}>
      <table>
        <caption>{status ? (status === "BORRADOR" ? "Resultados del borrador" : "Resultados de la libreta publicada")
          : draft ? "Resultados del borrador" : "Resultados de la libreta publicada"}</caption>
        <thead><tr>{["Competencia", "Evaluación", "Fecha", "Resultado", "Ponderación", "Observación"].map((label) => <th key={label} scope="col">{label}</th>)}</tr></thead>
        <tbody>{report.filas.map((row, index) => <tr key={index}>
          <th scope="row">{row.competencia}</th><td data-label="Evaluación">{row.evaluacion}</td><td data-label="Fecha">{row.fecha}</td>
          <td data-label="Resultado">{/^(AD|A|B|C)$/.test(row.resultado) ? <span className={styles.reportGrade}>{row.resultado}</span> : row.resultado}</td>
          <td data-label="Ponderación">{row.ponderacion}</td>
          <td data-label="Observación" className={styles.reportObservation}>{row.observacion}</td>
        </tr>)}</tbody>
      </table>
    </div>}
  </article>;
}
