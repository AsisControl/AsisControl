"use client";

import { useEffect, useState } from "react";
import { ApiError, api, message } from "@/core/api/client";
import { Feedback, Loading, PageHeader, Pager } from "@/shared/ui";
import { usePaged } from "@/shared/hooks/use-paged";
import { evaluationDate } from "./lib/labels";
import type { OwnAssignment } from "./types";
import type { AvailableReport, ProgressReport, ReportPeriod } from "./report-types";
import { generateReportDrafts, getProgressReportDraft, publishPeriodReports, getProgressReport, getProgressReportPdf, getReportPeriodStatus, type ReportPeriodStatus, listAvailableReports, listReportPeriods, saveProgressReportPdf } from "./services/report-service";
import styles from "./evaluations.module.css";
import { ReportContent, ReportBadge } from "./report-content";
import { ReportPdfDownload } from "./report-pdf-download";


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
  const [missing, setMissing] = useState(false);
  const [pending, setPending] = useState<"notes" | "pdf" | null>(null);
  const busy = pending !== null;
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
    setMissing(false);
  }

  async function open(download: boolean) {
    if (!option || busy) return;
    setPending(download ? "pdf" : "notes"); setError(""); setMissing(false);
    try {
      if (download) {
        const blob = await getProgressReportPdf(option);
        saveProgressReportPdf(blob);
      } else setDetail(await getProgressReport(option));
    } catch (e) {
      if (e instanceof ApiError && e.code === "INFORME_NO_ENCONTRADO") {
        setDetail(null);
        setMissing(true);
      } else {
        setError(download ? `No se pudo descargar el PDF. ${message(e)}` : message(e));
      }
    }
    finally { setPending(null); }
  }
  return <section>
    <PageHeader title="Progreso" description="Consulta las libretas publicadas por período y curso." />
    <Feedback error={error} />
    {!items && !error && <Loading skeleton="list" rows={3}>Cargando informes disponibles.</Loading>}

    {items && <>
      {(guardian && students.length > 0 || visible.length > 0) && <div className={`panel ${styles.reportPanel}`}>
        <div className={styles.reportFilters}>
          {guardian && <label className="field">Estudiante
            <select value={student} disabled={busy} onChange={e => {
              setStudent(e.target.value); setPeriod(""); changeCourse("");
            }}>
              <option value="">Selecciona un estudiante</option>
              {students.map(item => <option key={item.id} value={item.id}>{item.nombreCompleto}</option>)}
            </select>
          </label>}
          {visible.length > 0 && <>
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
          </>}
        </div>
        {visible.length > 0 && <div className={styles.reportActions}>
          <button className="primary-action" disabled={!option || busy} onClick={() => open(false)}>
            {pending === "notes" ? "Consultando…" : "Ver notas"}
          </button>
          <button className="secondary-action" disabled={!option || busy} onClick={() => open(true)}>
            {pending === "pdf" ? <><span className={styles.reportSpinner} aria-hidden="true" /> Generando PDF…</> : "Descargar libreta completa"}
          </button>
          {detail && option && <ReportPdfDownload key={`${option.idAlumno}:${option.idAsignacionCurso}:${option.idPeriodoAcademico}:${detail.periodo}`}
            context={option} report={detail} showFull={false} disabled={busy} />}
        </div>}
        {pending && <span className={styles.reportSrOnly} role="status">{pending === "pdf" ? "Generando PDF…" : "Consultando libreta…"}</span>}
      </div>}
      {!detail && !busy && !error && <p className={styles.reportNotice}>
        {missing ? "No hay una libreta publicada para este período y curso."
          : guardian && !students.length ? "No tienes estudiantes asociados."
          : (!guardian || student) && !visible.length ? "Todavía no hay libretas publicadas para consultar."
          : guardian ? "Selecciona un estudiante, período y curso para consultar su libreta publicada."
          : "Selecciona un período y curso para consultar tu libreta publicada."}
      </p>}
    </>}
    {detail && <ReportContent report={detail} sustento="publicado" />}
    {guardian && student && <FamilyTasks key={student} student={student} />}
  </section>;
}

function FamilyTasks({ student }: { student: string }) {
  const [open, setOpen] = useState(false);
  return <details className={`panel ${styles.reportPanel}`} onToggle={event => setOpen(event.currentTarget.open)}>
    <summary>Tareas del estudiante</summary>
    {open && <FamilyTaskList student={student} />}
  </details>;
}

function FamilyTaskList({ student }: { student: string }) {
  const { data, error, loading, setPage } = usePaged<{
    id: number; nombre: string; curso: string; fechaLimite: string;
  }>("/api/tareas/familia", "", `alumnoId=${encodeURIComponent(student)}`);
  return <>
    <Feedback error={error} />
    {loading && <Loading variant="inline">Cargando tareas…</Loading>}
    {data?.content.length === 0 && <p>No hay tareas publicadas para este estudiante.</p>}
    {!!data?.content.length && <ul>{data.content.map(task => <li key={task.id}>
      {task.nombre} · {task.curso} · Vence {evaluationDate(task.fechaLimite.slice(0, 10))} · Sin peso
    </li>)}</ul>}
    <Pager data={data} loading={loading} setPage={setPage} />
  </>;
}

export function PublishPeriod({ assignments, canWrite }: { assignments: OwnAssignment[]; canWrite: boolean }) {
  const [assignment, setAssignment] = useState("");
  const [periods, setPeriods] = useState<ReportPeriod[]>([]);
  const [period, setPeriod] = useState("");
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [status, setStatus] = useState<ReportPeriodStatus | null>(null);
  const [statusError, setStatusError] = useState("");
  const [refresh, setRefresh] = useState(0);
  const [preview, setPreview] = useState<{ report: ProgressReport; draft: boolean } | null>(null);

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
    getReportPeriodStatus(Number(assignment), Number(period))
      .then(data => { if (active) setStatus(data); })
      .catch(e => { if (active) setStatusError(message(e)); });
    return () => { active = false; };
  }, [assignment, period, refresh]);

  const selectedAssignment = assignments.find(item => String(item.id) === assignment);
  const selectedPeriod = periods.find(item => String(item.id) === period);
  const hasDrafts = status?.alumnos.some(item => item.borrador) ?? false;
  const allPublished = Boolean(!hasDrafts && status?.alumnos.length && status.alumnos.every(item => item.publicada));
  let periodNotice = "";
  if (status) {
    if (hasDrafts) {
      periodNotice = status.publicable
        ? "Hay un borrador pendiente con los cambios actuales. Revísalo y publícalo cuando esté listo."
        : "Hay borradores pendientes. Revísalos antes de publicar.";
    } else if (status.desactualizada) {
      periodNotice = "Las evaluaciones o notas cambiaron. Genera un nuevo borrador para actualizar la libreta.";
    } else if (allPublished) {
      periodNotice = "Todas las libretas del período están publicadas y actualizadas.";
    }
  }

  function reloadStatus() {
    setStatus(null);
    setStatusError("");
    setRefresh(value => value + 1);
  }

  function changePeriod(value: string) {
    setPeriod(value);
    setStatus(null);
    setStatusError("");
    setPreview(null);
    setConfirm(false);
    setSuccess("");
    setError("");
  }

  async function generate() {
    setBusy(true); setError(""); setSuccess(""); setPreview(null); setConfirm(false);
    try {
      const result = await generateReportDrafts(Number(assignment), Number(period));
      setSuccess(`Se generaron ${result.informesProcesados} borradores. Revisa los resultados antes de publicar.`);
      reloadStatus();
    } catch (e) { setError(message(e)); } finally { setBusy(false); }
  }

  async function review(idAlumno: number, draft: boolean) {
    const context = { idAlumno, idAsignacionCurso: Number(assignment), idPeriodoAcademico: Number(period) };
    setBusy(true); setError(""); setPreview(null);
    try {
      const report = await (draft ? getProgressReportDraft(context) : getProgressReport(context));
      setPreview({ report, draft });
    } catch (e) { setError(message(e)); } finally { setBusy(false); }
  }

  async function publish() {
    if (!canWrite || !selectedAssignment || !selectedPeriod || !status?.publicable) return;
    setBusy(true); setError(""); setSuccess("");
    try {
      const result = await publishPeriodReports(selectedAssignment.id, selectedPeriod.id);
      setSuccess(result.informesProcesados ? `Se publicaron ${result.informesProcesados} libretas.` : "No hay nuevas libretas para publicar.");
      setPreview(null);
    } catch (e) {
      setError(message(e));
    } finally {
      setConfirm(false);
      reloadStatus();
      setBusy(false);
    }
  }

  return <section className={styles.reportStack}>
    <header className={styles.reportHeading}>
      <h2>Libretas del período</h2>
      <p>Genera, revisa y publica las libretas de la sección para el curso y período elegidos.</p>
    </header>
    <Feedback error={error} success={success} />
    <div className={`panel ${styles.reportPanel}`}>
      <div className={styles.reportFilters}>
        <label className="field">Curso y sección
          <select value={assignment} disabled={busy} onChange={e => {
            setAssignment(e.target.value); setPeriods([]); changePeriod("");
          }}>
            <option value="">Selecciona un curso</option>
            {assignments.map(a => <option key={a.id} value={a.id}>{a.curso} · {a.seccion} · {a.anioAcademico}</option>)}
          </select>
        </label>
        <label className="field">Período académico
          <select value={period} disabled={busy || !periods.length} onChange={e => changePeriod(e.target.value)}>
            <option value="">Selecciona un período</option>
            {periods.map(p => <option key={p.id} value={p.id}>{p.nombre}</option>)}
          </select>
        </label>
      </div>
      {canWrite && <div className={styles.reportActions}>
        <button className="secondary-action" disabled={!period || busy} onClick={generate}>Generar borradores</button>
        {status?.publicable && !confirm && <button className="primary-action" disabled={busy} onClick={() => setConfirm(true)}>Publicar libretas</button>}
      </div>}
      {confirm && status?.publicable && <div className={styles.reportNotice}>
        <p>¿Confirmas la publicación de las libretas pendientes de {selectedAssignment?.curso}, sección {selectedAssignment?.seccion}, para {selectedPeriod?.nombre}?</p>
        <div className={styles.reportActions}>
          <button className="primary-action" disabled={busy} onClick={publish}>{busy ? "Publicando…" : "Confirmar publicación"}</button>
          <button className="secondary-action" disabled={busy} onClick={() => setConfirm(false)}>Cancelar</button>
        </div>
      </div>}
    </div>
    {busy && <Loading variant="inline">Procesando libretas…</Loading>}
    {period && !status && !statusError && <Loading variant="inline">Consultando estado de las libretas…</Loading>}
    {statusError && <div>
      <Feedback error={`No se pudo consultar el estado de las libretas. ${statusError}`} />
      <button className="secondary-action" disabled={busy} onClick={reloadStatus}>Reintentar consulta</button>
    </div>}
    {periodNotice && <p className={status?.desactualizada && !hasDrafts
      ? `${styles.reportNotice} ${styles.reportWarning}`
      : styles.reportNotice} role="status">{periodNotice}</p>}
    {status && !status.alumnos.some(item => item.borrador || item.publicada) && <p className={styles.reportNotice}>Todavía no hay libretas para este curso y período. Genera los borradores para empezar.</p>}
    {status && status.alumnos.some(item => item.borrador || item.publicada) && <div className={`panel ${styles.reportPanel}`}>
      <table className={styles.reportRoster}>
        <caption>Libretas por estudiante</caption>
        <thead><tr><th scope="col">Estudiante</th><th scope="col">Estado</th><th scope="col">Acción</th></tr></thead>
        <tbody>{status.alumnos.filter(item => item.borrador || item.publicada).map(item => <tr key={item.idAlumno}>
          <th scope="row">{item.alumno}</th>
          <td><div className={styles.reportActions}>
            {item.borrador && <ReportBadge status="BORRADOR" />}
            {item.publicada && <ReportBadge status="PUBLICADA" />}
          </div></td>
          <td><div className={styles.reportActions}>
            {item.borrador && <button className="secondary-action" aria-label={`Revisar borrador de ${item.alumno}`} disabled={busy} onClick={() => review(item.idAlumno, true)}>Revisar</button>}
            {item.publicada && <button className="secondary-action" aria-label={`Ver libreta de ${item.alumno}`} disabled={busy} onClick={() => review(item.idAlumno, false)}>Ver libreta</button>}
          </div></td>
        </tr>)}</tbody>
      </table>
    </div>}
    {preview && <ReportContent report={preview.report} draft={preview.draft} sustento="docente" />}
  </section>;
}
