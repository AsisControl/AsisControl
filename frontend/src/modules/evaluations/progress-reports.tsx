"use client";

import { useEffect, useState } from "react";
import { api, message } from "@/core/api/client";
import { Feedback, Loading, PageHeader } from "@/shared/ui";
import type { OwnAssignment } from "./types";
import type { AvailableReport, ProgressReport, ReportPeriod } from "./report-types";
import { generateReportDrafts, getProgressReportDraft, publishPeriodReports, getProgressReport, getProgressReportPdf, listAvailableReports, listReportPeriods, saveProgressReportPdf } from "./services/report-service";
import { ReportContent } from "./report-content";


type FamilyStudent = { id: number; nombreCompleto: string };
const periodKey = (item: AvailableReport) => `${item.anio}-${item.idPeriodoAcademico}`;

/** Consulta de libretas publicadas para el estudiante y su familia. */
export function ProgressReports({ guardian = false }: { guardian?: boolean }) {
  const [items, setItems] = useState<AvailableReport[] | null>(null);
  const [students, setStudents] = useState<FamilyStudent[]>([]);
  const [student, setStudent] = useState("");
  const [period, setPeriod] = useState("");
  const [detail, setDetail] = useState<ProgressReport | null>(null);
  const [selected, setSelected] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    Promise.all([
      listAvailableReports(),
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
    setError("");
  }

  async function open(download: boolean) {
    if (!option) return;
    setBusy(true); setError("");
    try {
      if (download) {
        const blob = await getProgressReportPdf(option);
        saveProgressReportPdf(blob, option);
      } else setDetail(await getProgressReport(option));
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
    {detail && <ReportContent report={detail} />}
  </section>;
}

export function PublishPeriod({ assignments, canWrite }: { assignments: OwnAssignment[]; canWrite: boolean }) {
  const [assignment, setAssignment] = useState("");
  const [periods, setPeriods] = useState<ReportPeriod[]>([]);
  const [period, setPeriod] = useState("");
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [drafts, setDrafts] = useState<AvailableReport[]>([]);
  const [published, setPublished] = useState<AvailableReport[] | null>(null);
  const [publishedError, setPublishedError] = useState("");
  const [refresh, setRefresh] = useState(0);
  const [preview, setPreview] = useState<{ report: ProgressReport; draft: boolean } | null>(null);
  const [student, setStudent] = useState("");
  useEffect(() => {
    if (!assignment) return;
    let active = true;
    listReportPeriods(Number(assignment))
      .then(data => { if (active) setPeriods(data); }).catch(e => { if (active) setError(message(e)); });
    return () => { active = false; };
  }, [assignment]);
  useEffect(() => {
    if (!assignment || !period) return;
    let active = true;
    listAvailableReports()
      .then(items => {
        if (active) setPublished(items.filter(item =>
          String(item.idAsignacionCurso) === assignment && String(item.idPeriodoAcademico) === period));
      })
      .catch(e => { if (active) setPublishedError(message(e)); });
    return () => { active = false; };
  }, [assignment, period, refresh]);

  const draftsByStudent = new Map(drafts.map(item => [item.idAlumno, item]));
  const publishedByStudent = new Map((published ?? []).map(item => [item.idAlumno, item]));
  const students = [...new Map([...publishedByStudent, ...draftsByStudent]).values()];
  const selectedAssignment = assignments.find(item => String(item.id) === assignment);
  const selectedPeriod = periods.find(item => String(item.id) === period);

  function reloadPublished() {
    setPublished(null);
    setPublishedError("");
    setRefresh(value => value + 1);
  }

  function changePeriod(value: string) {
    setPeriod(value);
    setDrafts([]);
    setPublished(null);
    setPublishedError("");
    setStudent("");
    setPreview(null);
    setConfirm(false);
    setSuccess("");
    setError("");
  }

  async function generate() {
    setBusy(true); setError(""); setSuccess(""); setPreview(null); setConfirm(false); setStudent(""); setDrafts([]);
    try {
      const result = await generateReportDrafts(Number(assignment), Number(period));
      setDrafts(result.informes);
      setSuccess(`Se generaron ${result.informesProcesados} borradores. Revisa los resultados antes de publicar.`);
    } catch (e) { setError(message(e)); } finally { setBusy(false); }
  }
  async function review(item: AvailableReport, draft: boolean) {
    setBusy(true); setError(""); setPreview(null);
    try {
      const report = await (draft ? getProgressReportDraft(item) : getProgressReport(item));
      setPreview({ report, draft });
    }
    catch (e) { setError(message(e)); } finally { setBusy(false); }
  }
  async function publish() {
    if (!canWrite || !selectedAssignment || !selectedPeriod) return;
    setBusy(true); setError(""); setSuccess("");
    try {
      const result = await publishPeriodReports(selectedAssignment.id, selectedPeriod.id);
      setSuccess(result.informesProcesados ? `Se publicaron ${result.informesProcesados} libretas. Los estudiantes y sus apoderados pueden consultar sus notas y descargar su informe.` : "No hay nuevas libretas para publicar. Genera borradores para preparar una nueva versión.");
      setConfirm(false); setPreview(null); setDrafts([]); setStudent("");
      reloadPublished();
    } catch (e) { setError(message(e)); } finally { setBusy(false); }
  }
  return <section className="panel" style={{ padding: 24 }}>
    <h2>Libretas del período</h2>
    <p>Publica las libretas de los estudiantes de la sección para el curso y período seleccionados. Revisa los borradores antes de publicar. Si cambian las notas, vuelve a generar los borradores. Todas las libretas pendientes deben estar completas.</p>
    <Feedback error={error} />{success && <p role="status">{success}</p>}
    <label className="field">Curso y sección<select value={assignment} disabled={busy} onChange={e => {
      setAssignment(e.target.value); setPeriods([]); changePeriod("");
    }}><option value="">Selecciona un curso</option>{assignments.map(a => <option key={a.id} value={a.id}>{a.curso} · {a.seccion} · {a.anioAcademico}</option>)}</select></label>
    <label className="field">Período académico<select value={period} disabled={busy || !periods.length} onChange={e => changePeriod(e.target.value)}>
      <option value="">Selecciona un período</option>{periods.map(p => <option key={p.id} value={p.id}>{p.nombre}</option>)}
    </select></label>
    {canWrite && <button className="primary-action" disabled={!period || busy} onClick={generate}>Generar borradores</button>}{" "}
    {busy && <Loading variant="inline">Procesando libretas…</Loading>}
    {period && !published && !publishedError && <Loading variant="inline">Consultando libretas publicadas…</Loading>}
    {publishedError && <>
      <Feedback error={`No se pudo consultar el estado de las libretas publicadas. ${publishedError}`} />
      <button type="button" disabled={busy} onClick={reloadPublished}>Reintentar consulta</button>
    </>}
    {period && published && !students.length && <p>No hay libretas publicadas para este curso y período.</p>}
    {!!students.length && <div className="user-table-wrap">
      <table className="user-table">
        <caption>Libretas por estudiante</caption>
        <thead><tr><th scope="col">Estudiante</th><th scope="col">Estado</th><th scope="col">Consulta</th></tr></thead>
        <tbody>{students.map(item => {
          const hasDraft = draftsByStudent.has(item.idAlumno);
          const publishedReport = publishedByStudent.get(item.idAlumno);
          return <tr key={item.idAlumno}>
            <th scope="row">{item.alumno}</th>
            <td>{hasDraft && "BORRADOR"}{hasDraft && publishedReport && " · "}{publishedReport && "PUBLICADA"}</td>
            <td>{publishedReport && <button type="button" disabled={busy} onClick={() => review(publishedReport, false)}>
              Ver libreta publicada de {item.alumno}
            </button>}</td>
          </tr>;
        })}</tbody>
      </table>
    </div>}
    {!!drafts.length && <div>
      <label className="field">Alumno para revisar<select value={student} disabled={busy} onChange={e => { setStudent(e.target.value); setPreview(null); }}>
        <option value="">Selecciona un alumno</option>{drafts.map(d => <option key={d.idAlumno} value={d.idAlumno}>{d.alumno}</option>)}
      </select></label>
      <button disabled={!student || busy} onClick={() => {
        const item = draftsByStudent.get(Number(student));
        if (item) void review(item, true);
      }}>Revisar borrador</button>
    </div>}
    {preview && <ReportContent report={preview.report} draft={preview.draft} />}
    {canWrite && (!confirm ? <button className="primary-action" disabled={!selectedPeriod || busy} onClick={() => setConfirm(true)}>
      Publicar libretas
    </button> : <div>
      <p>¿Confirmas la publicación de las libretas pendientes de {selectedAssignment?.curso}, sección {selectedAssignment?.seccion}, para {selectedPeriod?.nombre}? Los alumnos y apoderados vinculados podrán consultarlas y descargar su PDF.</p>
      <button className="primary-action" disabled={busy} onClick={publish}>
        {busy ? "Publicando…" : "Confirmar publicación"}
      </button>{" "}
      <button disabled={busy} onClick={() => setConfirm(false)}>Cancelar</button>
    </div>)}
  </section>;
}
