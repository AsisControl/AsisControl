"use client";

import { useEffect, useRef, useState } from "react";
import { Empty, ReadPanel } from "./progress-read-panel";
import { ReportBadge, ReportContent } from "./report-content";
import {
  obtenerLibretaAcademica,
  obtenerResumenAcademico,
  type AcademicReport,
  type AcademicReportSummary,
} from "./services/progress-service";
import styles from "./evaluations.module.css";

const levels = ["AD", "A", "B", "C"] as const;

export function ReportRoster({ query }: { query: string }) {
  const [load] = useState(() => () => obtenerResumenAcademico(query));
  return <ReadPanel load={load}>{summary => summary.estudiantes.length ? <Results summary={summary} />
    : <Empty>No hay estudiantes para los filtros seleccionados.</Empty>}</ReadPanel>;
}

function Results({ summary }: { summary: AcademicReportSummary }) {
  const [selected, setSelected] = useState<AcademicReport | null>(null);
  const reportsByStudent = new Map<number, AcademicReport[]>();
  for (const report of summary.libretas) {
    const versions = reportsByStudent.get(report.idAlumno);
    if (versions) versions.push(report);
    else reportsByStudent.set(report.idAlumno, [report]);
  }
  const students = summary.estudiantes.map(student => {
    const versions = reportsByStudent.get(student.id) ?? [];
    return { ...student, versions, published: versions.find(item => item.estado === "PUBLICADA") };
  });
  const published = students.flatMap(student => student.published ? [student.published] : []);
  const drafts = summary.libretas.filter(item => item.estado === "BORRADOR");
  const withoutSummary = published.filter(item => !Object.keys(item.logros).length && !item.pendientes && !item.noEvaluadas).length;
  return <>
    <section className="panel" aria-label="Resumen de libretas">
      <h4>Estado de publicación</h4>
      <ul>
        <li>Estudiantes: {students.length}</li>
        <li>Con libreta publicada: {published.length}</li>
        <li>Sin libreta publicada: {students.length - published.length}</li>
        <li>Borradores: {drafts.length} · Por revisar: {drafts.filter(item => !item.revisado).length}</li>
      </ul>
      <h4>Distribución de logro</h4>
      <p className="hint">Se usa la última libreta publicada de cada estudiante.</p>
      {published.length ? <>
      <table className={styles.achievementDistribution} aria-label="Distribución de logro">
        <thead><tr>{[...levels, "Pendientes", "Sin evaluar"].map(label => <th key={label} scope="col">{label}</th>)}</tr></thead>
        <tbody><tr>{levels.map(level => <td key={level}>{published.reduce((n, item) => n + (item.logros[level] ?? 0), 0)}</td>)}
          <td>{published.reduce((n, item) => n + item.pendientes, 0)}</td>
          <td>{published.reduce((n, item) => n + item.noEvaluadas, 0)}</td>
        </tr></tbody>
      </table>
      {withoutSummary > 0 && <p className="hint">Libretas sin consolidado de competencias: {withoutSummary}</p>}
      </> : <p className="empty">Todavía no hay libretas publicadas en esta consulta.</p>}
    </section>
    <div className={`panel ${styles.reportPanel}`}>
      <table className={styles.reportRoster}>
        <caption>Libretas por estudiante</caption>
        <thead><tr><th scope="col">Estudiante</th><th scope="col">Estado y versión</th><th scope="col">Acción</th></tr></thead>
        <tbody>{students.map(({ id, nombre, codigoAlumno, versions, published }) => <tr key={id}>
          <th scope="row">{nombre}<br /><small>{codigoAlumno}</small></th>
          <td>{!published && <p>Sin libreta publicada</p>}{versions.map(item => <div key={item.id} className={styles.reportActions}>
            <ReportBadge status={item.estado} /> v{item.numeroVersion}
            {item.estado === "BORRADOR" && (item.revisado ? " · Revisado" : " · Por revisar")}
          </div>)}</td>
          <td>{versions.length === 0 && "—"}{versions.map(item => <div key={item.id}><button type="button" className="secondary-action"
            aria-label={`Ver ${item.estado.toLowerCase()} de ${item.alumno}, versión ${item.numeroVersion}`}
            onClick={() => setSelected(item)}>Ver {item.estado === "BORRADOR" ? "borrador" : "publicada"}</button></div>)}</td>
        </tr>)}</tbody>
      </table>
    </div>
    {selected && <Detail key={selected.id} item={selected} close={() => setSelected(null)} />}
  </>;
}

function Detail({ item, close }: { item: AcademicReport; close: () => void }) {
  const [load] = useState(() => () => obtenerLibretaAcademica(item.id));
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { heading.current?.focus(); }, []);
  return <section className="panel" aria-label={`Libreta de ${item.alumno}`}>
    <h4 ref={heading} tabIndex={-1}>Libreta de {item.alumno}</h4>
    <button type="button" className="secondary-action" onClick={close}>Cerrar detalle</button>
    <ReadPanel load={load}>{report => <ReportContent report={report} draft={item.estado === "BORRADOR"} sustento="docente" />}</ReadPanel>
  </section>;
}
