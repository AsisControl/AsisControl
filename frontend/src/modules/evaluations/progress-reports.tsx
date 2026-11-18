"use client";

import { useEffect, useState } from "react";
import { api, apiBlob, message } from "@/core/api/client";
import { Feedback, PageHeader, Loading } from "@/shared/ui";
import type { OwnAssignment } from "./types";

type Option = { idAlumno: number; alumno: string; idAsignacionCurso: number; curso: string;
  anio: number; idPeriodoAcademico: number; periodo: string };
type Detail = { filas: { competencia: string; evaluacion: string; fecha: string;
  resultado: string; ponderacion: string; observacion: string }[] };
const query = (item: Option) => new URLSearchParams({ idAlumno: String(item.idAlumno),
  idAsignacionCurso: String(item.idAsignacionCurso), idPeriodoAcademico: String(item.idPeriodoAcademico) });

/** Opciones calculadas por el servidor a partir de la identidad y las notas publicadas. */
export function ProgressReports() {
  const [items, setItems] = useState<Option[] | null>(null);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [selected, setSelected] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    api<Option[]>("/api/calificaciones/informes-disponibles")
      .then(data => { if (active) setItems(data); })
      .catch(e => { if (active) setError(message(e)); });
    return () => { active = false; };
  }, []);
  const option = selected === "" ? undefined : items?.[Number(selected)];
  async function open(download: boolean) {
    if (!option) return;
    setBusy(true); setError("");
    try {
      if (download) {
        const blob = await apiBlob(`/api/calificaciones/reporte-avance?${query(option)}`, { timeoutMs: 60000 });
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement("a"); anchor.href = url;
        anchor.download = `avance-${option.idAlumno}-${option.idAsignacionCurso}-${option.idPeriodoAcademico}.pdf`;
        document.body.appendChild(anchor); anchor.click(); anchor.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      } else setDetail(await api<Detail>(`/api/calificaciones/avance?${query(option)}`));
    } catch (e) { setError(message(e)); }
    finally { setBusy(false); }
  }
  return <section>
    <PageHeader title="Progreso" description="Consulta las notas publicadas por el docente y descarga tu informe por período." />
    <Feedback error={error} />
    {!items && !error && <Loading skeleton="list" rows={3}>Cargando informes disponibles.</Loading>}
    {items?.length === 0 && <p>Todavía no hay notas publicadas para consultar.</p>}
    {!!items?.length && <>
      <label className="field">Alumno, curso y período
        <select value={selected} disabled={busy} onChange={e => { setSelected(e.target.value); setDetail(null); setError(""); }}>
          <option value="">Selecciona un informe</option>
          {items.map((item, index) => <option key={`${item.idAlumno}-${item.idAsignacionCurso}-${item.idPeriodoAcademico}`} value={index}>
            {item.alumno} · {item.curso} · {item.periodo} · {item.anio}
          </option>)}
        </select>
      </label>
      <button className="primary-action" disabled={!option || busy} onClick={() => open(false)}>Ver notas</button>{" "}
      <button className="primary-action" disabled={!option || busy} onClick={() => open(true)}>Descargar PDF</button>
      {busy && <p role="status">Preparando informe.</p>}
      {detail && <div className="user-table-wrap" style={{ marginTop: 24 }}><table className="user-table">
        <thead><tr>{["Competencia", "Evaluación", "Fecha", "Nivel", "Peso", "Observación"].map(t => <th key={t}>{t}</th>)}</tr></thead>
        <tbody>{detail.filas.map((row, i) => <tr key={i}>
          <td>{row.competencia}</td><td>{row.evaluacion}</td><td>{row.fecha}</td>
          <td>{row.resultado}</td><td>{row.ponderacion}</td><td>{row.observacion}</td>
        </tr>)}</tbody>
      </table></div>}
    </>}
  </section>;
}

export function PublishPeriod({ assignments, canWrite }: { assignments: OwnAssignment[]; canWrite: boolean }) {
  const [assignment, setAssignment] = useState("");
  const [periods, setPeriods] = useState<{ id: number; nombre: string }[]>([]);
  const [period, setPeriod] = useState("");
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  useEffect(() => {
    if (!assignment) return;
    let active = true;
    api<{ id: number; nombre: string }[]>(`/api/evaluaciones/periodos-informe?idAsignacionCurso=${assignment}`)
      .then(data => { if (active) setPeriods(data); }).catch(e => { if (active) setError(message(e)); });
    return () => { active = false; };
  }, [assignment]);
  async function publish() {
    setBusy(true); setError(""); setSuccess("");
    try {
      const result = await api<{ evaluacionesPublicadas: number }>("/api/evaluaciones/publicar-periodo", {
        method: "POST", body: JSON.stringify({ idAsignacionCurso: Number(assignment), idPeriodoAcademico: Number(period) }),
        timeoutMs: 60000,
      });
      setSuccess(result.evaluacionesPublicadas ? `Se publicaron ${result.evaluacionesPublicadas} evaluaciones. Los estudiantes y sus apoderados pueden consultar sus notas y descargar su informe.` : "No hay evaluaciones en borrador para publicar en este período.");
      setConfirm(false);
    } catch (e) { setError(message(e)); } finally { setBusy(false); }
  }
  return <section className="panel" style={{ padding: 24 }}>
    <h2>Publicar notas del período</h2>
    <p>Publica todas las evaluaciones en borrador del curso y período. Cada planilla debe estar completa para publicar. Las notas ya publicadas conservan su estado.</p>
    <Feedback error={error} />{success && <p role="status">{success}</p>}
    <label className="field">Curso y sección<select value={assignment} disabled={busy} onChange={e => {
      setAssignment(e.target.value); setPeriod(""); setPeriods([]); setConfirm(false); setError(""); setSuccess("");
    }}><option value="">Selecciona un curso</option>{assignments.map(a => <option key={a.id} value={a.id}>{a.curso} · {a.seccion} · {a.anioAcademico}</option>)}</select></label>
    <label className="field">Período académico<select value={period} disabled={busy || !periods.length} onChange={e => { setPeriod(e.target.value); setConfirm(false); setSuccess(""); }}>
      <option value="">Selecciona un período</option>{periods.map(p => <option key={p.id} value={p.id}>{p.nombre}</option>)}
    </select></label>
    {canWrite && (!confirm ? <button className="primary-action" disabled={!period || busy} onClick={() => setConfirm(true)}>Publicar notas</button> : <div>
      <p>¿Confirmas la publicación? Los alumnos y apoderados vinculados podrán ver sus notas y generar su PDF.</p>
      <button className="primary-action" disabled={busy} onClick={publish}>{busy ? "Publicando…" : "Confirmar publicación"}</button>{" "}
      <button disabled={busy} onClick={() => setConfirm(false)}>Cancelar</button>
    </div>)}
  </section>;
}
