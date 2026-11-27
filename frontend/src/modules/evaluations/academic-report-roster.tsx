"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/core/api/client";
import { Empty, ReadPanel } from "./progress-read-panel";
import { ReportBadge, ReportContent } from "./report-content";
import type { ProgressReport } from "./report-types";
import styles from "./evaluations.module.css";

const PATH = "/api/calificaciones/informes/academicos";
const levels = ["AD", "A", "B", "C"] as const;
type Report = {
  id: number; idAlumno: number; alumno: string; codigoAlumno: string | null;
  estado: "PUBLICADA" | "BORRADOR"; numeroVersion: number; revisado: boolean;
  logros: Partial<Record<typeof levels[number], number>>; pendientes: number; noEvaluadas: number;
};

export function ReportRoster({ query }: { query: string }) {
  const [load] = useState(() => () => api<Report[]>(`${PATH}?${query}`));
  return <ReadPanel load={load}>{items => items.length ? <Results items={items} />
    : <Empty>No hay libretas para los filtros seleccionados.</Empty>}</ReadPanel>;
}

function Results({ items }: { items: Report[] }) {
  const [selected, setSelected] = useState<Report | null>(null);
  const students = new Map<number, Report[]>();
  for (const report of items) {
    const versions = students.get(report.idAlumno);
    if (versions) versions.push(report);
    else students.set(report.idAlumno, [report]);
  }
  // La consulta ordena las versiones de cada estudiante de mayor a menor.
  const latest = Array.from(students.values(), versions => versions[0]);
  const drafts = items.filter(item => item.estado === "BORRADOR");
  const withoutSummary = latest.filter(item => !Object.keys(item.logros).length && !item.pendientes && !item.noEvaluadas).length;
  return <>
    <section className="panel" aria-label="Resumen de libretas">
      <h4>Estado de publicación</h4>
      <p>Estudiantes con libreta: {students.size} · Con versión publicada: {items.length - drafts.length} · Con borrador: {drafts.length} · Borradores por revisar: {drafts.filter(item => !item.revisado).length}</p>
      <h4>Distribución de logro</h4>
      <p className="hint">Competencias de la última versión de cada estudiante.</p>
      <table className={styles.achievementDistribution} aria-label="Distribución de logro">
        <thead><tr>{[...levels, "Pendientes", "Sin evaluar"].map(label => <th key={label} scope="col">{label}</th>)}</tr></thead>
        <tbody><tr>{levels.map(level => <td key={level}>{latest.reduce((n, item) => n + (item.logros[level] ?? 0), 0)}</td>)}
          <td>{latest.reduce((n, item) => n + item.pendientes, 0)}</td>
          <td>{latest.reduce((n, item) => n + item.noEvaluadas, 0)}</td>
        </tr></tbody>
      </table>
      {withoutSummary > 0 && <p className="hint">Libretas sin consolidado de competencias: {withoutSummary}</p>}
    </section>
    <div className={`panel ${styles.reportPanel}`}>
      <table className={styles.reportRoster}>
        <caption>Libretas por estudiante</caption>
        <thead><tr><th scope="col">Estudiante</th><th scope="col">Estado y versión</th><th scope="col">Acción</th></tr></thead>
        <tbody>{Array.from(students, ([id, versions]) => <tr key={id}>
          <th scope="row">{versions[0].alumno}<br /><small>{versions[0].codigoAlumno}</small></th>
          <td>{versions.map(item => <div key={item.id} className={styles.reportActions}>
            <ReportBadge status={item.estado} /> v{item.numeroVersion}
            {item.estado === "BORRADOR" && (item.revisado ? " · Revisado" : " · Por revisar")}
          </div>)}</td>
          <td>{versions.map(item => <div key={item.id}><button type="button" className="secondary-action"
            aria-label={`Ver ${item.estado.toLowerCase()} de ${item.alumno}, versión ${item.numeroVersion}`}
            onClick={() => setSelected(item)}>Ver {item.estado === "BORRADOR" ? "borrador" : "publicada"}</button></div>)}</td>
        </tr>)}</tbody>
      </table>
    </div>
    {selected && <Detail key={selected.id} item={selected} close={() => setSelected(null)} />}
  </>;
}

function Detail({ item, close }: { item: Report; close: () => void }) {
  const [load] = useState(() => () => api<ProgressReport>(`${PATH}/${item.id}`));
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { heading.current?.focus(); }, []);
  return <section className="panel" aria-label={`Libreta de ${item.alumno}`}>
    <h4 ref={heading} tabIndex={-1}>Libreta de {item.alumno}</h4>
    <button type="button" className="secondary-action" onClick={close}>Cerrar detalle</button>
    <ReadPanel load={load}>{report => <ReportContent report={report} draft={item.estado === "BORRADOR"} sustento="docente" />}</ReadPanel>
  </section>;
}
