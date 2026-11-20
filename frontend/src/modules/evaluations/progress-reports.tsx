"use client";

import { useEffect, useState } from "react";
import { api, apiBlob, message } from "@/core/api/client";
import { Feedback, PageHeader, Loading } from "@/shared/ui";
import { saveBlob } from "@/shared/lib/save-blob";
import type { OwnAssignment } from "./types";
import type { ProgressReportRow } from "./report-types";
import { ReportEvidenceDialog } from "./report-evidence-dialog";
import styles from "./evaluations.module.css";

type Option = { idAlumno: number; alumno: string; idAsignacionCurso: number; curso: string;
  anio: number; idPeriodoAcademico: number; periodo: string };
type Detail = { periodo?: string; filas: ProgressReportRow[] };
const query = (item: Option) => new URLSearchParams({ idAlumno: String(item.idAlumno),
  idAsignacionCurso: String(item.idAsignacionCurso), idPeriodoAcademico: String(item.idPeriodoAcademico) });

type FamilyStudent = { id: number; nombreCompleto: string };
const periodKey = (item: Option) => `${item.anio}-${item.idPeriodoAcademico}`;

/** Consulta de libretas publicadas para el estudiante y su familia. */
export function ProgressReports({ guardian = false }: { guardian?: boolean }) {
  const [items, setItems] = useState<Option[] | null>(null);
  const [students, setStudents] = useState<FamilyStudent[]>([]);
  const [student, setStudent] = useState("");
  const [period, setPeriod] = useState("");
  const [detail, setDetail] = useState<Detail | null>(null);
  const [evidenceRow, setEvidenceRow] = useState<ProgressReportRow | null>(null);
  const [selected, setSelected] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    Promise.all([
      api<Option[]>("/api/calificaciones/informes-disponibles"),
      guardian ? api<FamilyStudent[]>("/api/familia/estudiantes") : Promise.resolve([]),
    ]).then(([reports, family]) => {
      if (!active) return;
      setItems(reports);
      setStudents(family);
      if (family.length === 1) setStudent(String(family[0].id));
    }).catch(e => { if (active) setError(message(e)); });
    return () => { active = false; };
  }, [guardian]);
  const visible = (items ?? []).filter(item => !guardian || String(item.idAlumno) === student);
  const periods = [...new Map(visible.map(item => [periodKey(item), item])).values()];
  const courses = visible.filter(item => periodKey(item) === period);
  const option = courses.find(item => String(item.idAsignacionCurso) === selected);

  function changeCourse(value: string) {
    setSelected(value);
    setDetail(null);
    setEvidenceRow(null);
    setError("");
  }

  async function open(download: boolean) {
    if (!option) return;
    setBusy(true); setError("");
    try {
      if (download) {
        const blob = await apiBlob(`/api/calificaciones/reporte-avance?${query(option)}`, { timeoutMs: 60000 });
        saveBlob(blob, `avance-${option.idAlumno}-${option.idAsignacionCurso}-${option.idPeriodoAcademico}.pdf`);
      } else {
        setDetail(null);
        setEvidenceRow(null);
        const report = await api<Detail>(`/api/calificaciones/avance?${query(option)}`);
        if (report.periodo?.endsWith(" | BORRADOR")) {
          setError("No hay una libreta publicada para este período y curso.");
        } else {
          setDetail(report);
        }
      }
    } catch (e) { setError(message(e)); }
    finally { setBusy(false); }
  }
  return <section>
    <PageHeader title="Progreso" description="Consulta las libretas publicadas por período y curso." />
    <Feedback error={error} />
    {!items && !error && <Loading skeleton="list" rows={3}>Cargando informes disponibles.</Loading>}
    {items && guardian && (students.length === 0 ? <p>No tienes estudiantes asociados.</p> :
      <label className="field">Estudiante
        <select value={student} disabled={busy} onChange={e => {
          setStudent(e.target.value);
          setPeriod("");
          changeCourse("");
        }}>
          <option value="">Selecciona un estudiante</option>
          {students.map(item => <option key={item.id} value={item.id}>{item.nombreCompleto}</option>)}
        </select>
      </label>)}
    {items && (!guardian || student) && (visible.length === 0 ?
      <p>Todavía no hay libretas publicadas para consultar.</p> : <>
        <label className="field">Período académico
          <select value={period} disabled={busy} onChange={e => { setPeriod(e.target.value); changeCourse(""); }}>
            <option value="">Selecciona un período</option>
            {periods.map(item => <option key={periodKey(item)} value={periodKey(item)}>{item.periodo} · {item.anio}</option>)}
          </select>
        </label>
        {period && <label className="field">Curso
          <select value={selected} disabled={busy} onChange={e => changeCourse(e.target.value)}>
            <option value="">Selecciona un curso</option>
            {courses.map(item => <option key={item.idAsignacionCurso} value={item.idAsignacionCurso}>{item.curso}</option>)}
          </select>
        </label>}
        <button className="primary-action" disabled={!option || busy} onClick={() => open(false)}>Ver notas</button>{" "}
        <button className="primary-action" disabled={!option || busy} onClick={() => open(true)}>Descargar PDF</button>
      </>)}
      {busy && <p role="status">Preparando informe.</p>}
      {detail && <div className="user-table-wrap" style={{ marginTop: 24 }}><p>{detail.periodo}</p><table className="user-table">
        <thead><tr>{["Competencia", "Evaluación", "Fecha", "Nivel", "Peso", "Observación"].map(t => <th key={t}>{t}</th>)}</tr></thead>
        <tbody>{detail.filas.map((row, i) => <tr key={row.idDetalleInforme || i}>
          <td>{row.competencia}</td><td>{row.evaluacion}</td><td>{row.fecha}</td>
          <td>{row.resultado}</td><td>{row.ponderacion}</td><td>
            {row.observacion}
            {row.idInforme > 0 && row.idDetalleInforme > 0 && <div className={styles.reportEvidenceAction}>
              <button type="button" className="secondary-action" aria-haspopup="dialog"
                aria-label={`Ver sustento de ${row.competencia} en ${row.evaluacion}`}
                onClick={() => setEvidenceRow(row)}>Ver sustento</button>
            </div>}
          </td>
        </tr>)}</tbody>
      </table></div>}
    {evidenceRow && <ReportEvidenceDialog key={evidenceRow.idDetalleInforme}
      row={evidenceRow} onClose={() => setEvidenceRow(null)} />}
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
