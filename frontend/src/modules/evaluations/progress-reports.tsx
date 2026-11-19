"use client";

import { useEffect, useState } from "react";
import { api, apiBlob, message } from "@/core/api/client";
import { Feedback, PageHeader, Loading } from "@/shared/ui";
import type { OwnAssignment } from "./types";

type Option = { idAlumno: number; alumno: string; idAsignacionCurso: number; curso: string;
  anio: number; idPeriodoAcademico: number; periodo: string };
type Detail = { periodo?: string; filas: { competencia: string; evaluacion: string; fecha: string;
  resultado: string; ponderacion: string; observacion: string }[] };
const query = (item: Option) => new URLSearchParams({ idAlumno: String(item.idAlumno),
  idAsignacionCurso: String(item.idAsignacionCurso), idPeriodoAcademico: String(item.idPeriodoAcademico) });

/** Opciones autorizadas de libretas persistidas y publicadas. */
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
    <PageHeader title="Progreso" description="Consulta las libretas publicadas por el docente y descarga tu informe por período." />
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
      {detail && <div className="user-table-wrap" style={{ marginTop: 24 }}><p>{detail.periodo}</p><table className="user-table">
        <thead><tr>{["Competencia", "Evaluación", "Fecha", "Nivel", "Peso", "Observación"].map(t => <th key={t}>{t}</th>)}</tr></thead>
        <tbody>{detail.filas.map((row, i) => <tr key={i}>
          <td>{row.competencia}</td><td>{row.evaluacion}</td><td>{row.fecha?.split("-").reverse().join("/")}</td>
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
  const [drafts, setDrafts] = useState<Option[]>([]);
  const [preview, setPreview] = useState<Detail | null>(null);
  const [student, setStudent] = useState("");
  useEffect(() => {
    if (!assignment) return;
    let active = true;
    api<{ id: number; nombre: string }[]>(`/api/evaluaciones/periodos-informe?idAsignacionCurso=${assignment}`)
      .then(data => { if (active) setPeriods(data); }).catch(e => { if (active) setError(message(e)); });
    return () => { active = false; };
  }, [assignment]);
  async function generate() {
    setBusy(true); setError(""); setSuccess(""); setPreview(null); setConfirm(false); setStudent(""); setDrafts([]);
    try {
      const result = await api<{ informesProcesados: number; informes: Option[] }>("/api/calificaciones/informes/generar", {
        method: "POST", body: JSON.stringify({ idAsignacionCurso: Number(assignment), idPeriodoAcademico: Number(period) }), timeoutMs: 60000,
      });
      setDrafts(result.informes);
      setSuccess(`Se generaron ${result.informesProcesados} borradores. Revisa los resultados antes de publicar.`);
    } catch (e) { setError(message(e)); } finally { setBusy(false); }
  }
  async function review() {
    const item = drafts.find(d => String(d.idAlumno) === student);
    if (!item) return;
    setBusy(true); setError("");
    try { setPreview(await api<Detail>(`/api/calificaciones/informes/borrador?${query(item)}`)); }
    catch (e) { setError(message(e)); } finally { setBusy(false); }
  }
  async function publish() {
    setBusy(true); setError(""); setSuccess("");
    try {
      const result = await api<{ informesProcesados: number }>("/api/calificaciones/informes/publicar", {
        method: "POST", body: JSON.stringify({ idAsignacionCurso: Number(assignment), idPeriodoAcademico: Number(period) }),
        timeoutMs: 60000,
      });
      setSuccess(result.informesProcesados ? `Se publicaron ${result.informesProcesados} libretas. Los estudiantes y sus apoderados pueden consultar sus notas y descargar su informe.` : "No hay nuevas libretas para publicar. Genera borradores para preparar una nueva versión.");
      setConfirm(false); setPreview(null); setDrafts([]); setStudent("");
    } catch (e) { setError(message(e)); } finally { setBusy(false); }
  }
  return <section className="panel" style={{ padding: 24 }}>
    <h2>Libretas del período</h2>
    <p>Genera los borradores del curso y período y revisa sus resultados. Para actualizarlos, vuelve a generarlos. Publicar requiere todos los niveles completos; las versiones anteriores se conservan.</p>
    <Feedback error={error} />{success && <p role="status">{success}</p>}
    <label className="field">Curso y sección<select value={assignment} disabled={busy} onChange={e => {
      setDrafts([]); setPreview(null); setStudent(""); setAssignment(e.target.value); setPeriod(""); setPeriods([]); setConfirm(false); setError(""); setSuccess("");
    }}><option value="">Selecciona un curso</option>{assignments.map(a => <option key={a.id} value={a.id}>{a.curso} · {a.seccion} · {a.anioAcademico}</option>)}</select></label>
    <label className="field">Período académico<select value={period} disabled={busy || !periods.length} onChange={e => { setDrafts([]); setPreview(null); setStudent(""); setPeriod(e.target.value); setConfirm(false); setSuccess(""); }}>
      <option value="">Selecciona un período</option>{periods.map(p => <option key={p.id} value={p.id}>{p.nombre}</option>)}
    </select></label>
    {canWrite && <button className="primary-action" disabled={!period || busy} onClick={generate}>Generar borradores</button>}{" "}
    {!!drafts.length && <div>
      <label className="field">Alumno para revisar<select value={student} disabled={busy} onChange={e => { setStudent(e.target.value); setPreview(null); }}>
        <option value="">Selecciona un alumno</option>{drafts.map(d => <option key={d.idAlumno} value={d.idAlumno}>{d.alumno}</option>)}
      </select></label>
      <button disabled={!student || busy} onClick={review}>Revisar borrador</button>
      {preview && <div className="user-table-wrap"><p>{preview.periodo}</p><table className="user-table">
        <thead><tr><th>Competencia</th><th>Evaluación</th><th>Fecha</th><th>Nivel</th><th>Peso</th><th>Observación</th></tr></thead>
        <tbody>{preview.filas.map((f, i) => <tr key={i}><td>{f.competencia}</td><td>{f.evaluacion}</td><td>{f.fecha}</td><td>{f.resultado}</td><td>{f.ponderacion}</td><td>{f.observacion}</td></tr>)}</tbody>
      </table></div>}
    </div>}
    {canWrite && (!confirm ? <button className="primary-action" disabled={!period || busy} onClick={() => setConfirm(true)}>Publicar libretas</button> : <div>
      <p>¿Confirmas la publicación? Los alumnos y apoderados vinculados podrán ver sus notas y generar su PDF.</p>
      <button className="primary-action" disabled={busy} onClick={publish}>{busy ? "Publicando…" : "Confirmar publicación"}</button>{" "}
      <button disabled={busy} onClick={() => setConfirm(false)}>Cancelar</button>
    </div>)}
  </section>;
}
