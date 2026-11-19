import { dateLabel } from "@/shared/lib/formatters";
import { LEVEL_SCALE } from "./lib/labels";
import type { ProgressReport } from "./report-types";
import styles from "./evaluations.module.css";

export function ReportContent({ report, draft = false }: { report: ProgressReport; draft?: boolean }) {
  return <article className={`panel ${styles.sheet}`} aria-label="Informe de progreso por período">
    <div className={styles.sheetHeading}><div>
      <h2>{report.alumno}</h2>
      <p>Código: {report.codigoAlumno || "Sin código de estudiante"}</p>
      <p>{report.curso} · {report.grado}° {report.seccion} · {report.anio}</p>
      <p>Docente: {report.docente}</p>
      <p>{report.periodo} · {report.fechas}</p>
      <p>Generado: {dateLabel(report.generadoEn)} (Lima)</p>
    </div></div>
    <p className="hint">{LEVEL_SCALE}</p>
    {!report.filas.length ? <p>No hay resultados disponibles en este informe.</p> : <div className={styles.sheetTable}>
      <table>
        <caption>{draft ? "Resultados del borrador" : "Resultados de la libreta publicada"}</caption>
        <thead><tr>{["Competencia", "Evaluación", "Fecha", "Resultado", "Ponderación", "Observación"].map((label) => <th key={label} scope="col">{label}</th>)}</tr></thead>
        <tbody>{report.filas.map((row, index) => <tr key={index}>
          <th scope="row">{row.competencia}</th><td>{row.evaluacion}</td><td>{row.fecha}</td>
          <td>{row.resultado}</td><td>{row.ponderacion}</td>
          <td className={styles.reportObservation}>{row.observacion}</td>
        </tr>)}</tbody>
      </table>
    </div>}
  </article>;
}
