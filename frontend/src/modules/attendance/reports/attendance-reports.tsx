"use client";

import { useEffect, useId, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Dialog, Feedback, Loading, Pager, PanelHeading, StatusPill } from "@/shared/ui";
import { attendanceReport, reportOptions, type Options, type Report, type ReportRow, type ReportState, type ReportType } from "./report-service";
import styles from "./attendance-reports.module.css";
import { AdminSessionCorrection } from "./admin-session-correction";
import { LastAttendanceCorrection } from "./last-attendance-correction";
import { TeacherMarkingCorrection } from "./teacher-marking-correction";

const LABELS: Record<string, string> = {
  PRESENTE: "Presente", TARDANZA: "Tardanza", INASISTENCIA: "Inasistencia", SIN_MARCACION: "Sin marcación",
  TARDANZA_JUSTIFICADA: "Tardanza justificada", INASISTENCIA_JUSTIFICADA: "Inasistencia justificada",
};
const dateLabel = (date: string) => date.split("-").reverse().join("/");

export function AttendanceReports({ canCorrect = false }: { canCorrect?: boolean }) {
  const [options, setOptions] = useState<Options | null>(null);
  const [optionsError, setOptionsError] = useState("");
  const [type, setType] = useState<ReportType>("ESTUDIANTES");
  const [query, setQuery] = useState("tipo=ESTUDIANTES");
  const [refresh, setRefresh] = useState(0);
  const [validation, setValidation] = useState("");
  const dateErrorId = useId();
  const [editing, setEditing] = useState(false);
  useEffect(() => {
    let alive = true;
    reportOptions().then(data => { if (alive) { setOptions(data); setOptionsError(""); } })
      .catch(error => { if (alive) setOptionsError(message(error)); });
    return () => { alive = false; };
  }, [refresh]);
  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const desde = String(values.get("desde") || ""), hasta = String(values.get("hasta") || "");
    if (desde && hasta && desde > hasta) {
      setValidation("La fecha final debe ser igual o posterior a la inicial.");
      (event.currentTarget.elements.namedItem("hasta") as HTMLInputElement)?.focus();
      return;
    }
    const params = new URLSearchParams({ tipo: type });
    for (const [name, value] of values.entries()) if (String(value).trim()) params.set(name, String(value).trim());
    setValidation(""); setQuery(params.toString()); setRefresh(n => n + 1);
  }
  return <section className="panel">
    <div hidden={editing}>
    <PanelHeading title="Reportes de asistencia" summary="Consulta indicadores institucionales y los registros que los sustentan."
      actions={<button className="secondary-action" onClick={() => setRefresh(n => n + 1)}>Actualizar reporte</button>} />
    <Feedback error={optionsError} />
    <label className="field">Tipo de reporte
      <select value={type} onChange={e => {
        const value = e.target.value as ReportType;
        setType(value); setQuery(`tipo=${value}`); setValidation("");
      }}><option value="ESTUDIANTES">Estudiantes</option><option value="DOCENTES">Docentes</option></select>
    </label>
    <form key={type} className={styles.filters} onSubmit={apply}>
      <label className="field">Período<select name="periodoId" disabled={!options}>
        <option value="">Todos los períodos</option>
        {options?.periodos.map(item => <option key={item.id} value={item.id}>{item.nombre}</option>)}
      </select></label>
      <label className="field">Sección<select name="seccionId" disabled={!options}>
        <option value="">Todas las secciones</option>
        {options?.secciones.map(item => <option key={item.id} value={item.id}>{item.nombre}</option>)}
      </select></label>
      <label className="field">Buscar persona<input name="busqueda" maxLength={100} placeholder="Nombres o apellidos" /></label>
      <label className="field">Desde<input type="date" name="desde" onChange={() => setValidation("")} /></label>
      <label className="field">Hasta<input type="date" name="hasta" onChange={() => setValidation("")}
        aria-invalid={validation ? true : undefined} aria-describedby={validation ? dateErrorId : undefined} />
        {validation && <small id={dateErrorId} className="field-error" role="alert">{validation}</small>}
      </label>
      <div className={styles.filterActions}><button className="primary-action" type="submit">Aplicar filtros</button></div>
    </form>
    {type === "DOCENTES" && <p className={styles.explanation}>Cada registro corresponde a una jornada docente. Las omisiones se muestran solo para jornadas guardadas por el sistema y ya cerradas; no se reconstruyen ausencias de días anteriores. El filtro de sección selecciona docentes con una clase programada allí ese día.</p>}
    </div>
    <ReportResults key={`${query}:${refresh}`} query={query} type={type} canCorrect={canCorrect} onEditingChange={setEditing} />
  </section>;
}

function ReportResults({ query, type, canCorrect, onEditingChange }: {
  query: string; type: ReportType; canCorrect: boolean; onEditingChange: (editing: boolean) => void;
}) {
  const [state, setState] = useState<ReportState | "">("");
  const [page, setPage] = useState(0);
  const [selection, setSelection] = useState<ReportRow | null>(null);
  const [correctionTarget, setCorrectionTarget] = useState<{ sessionId: number; attendanceId: number } | null>(null);
  const [revision, setRevision] = useState(0);
  const [notice, setNotice] = useState("");
  if (correctionTarget !== null) return <AdminSessionCorrection {...correctionTarget}
    onCancel={() => { setCorrectionTarget(null); onEditingChange(false); }} onSaved={() => {
      onEditingChange(false);
      setCorrectionTarget(null); setPage(0); setRevision(n => n + 1); setNotice("Corrección guardada. El reporte se actualizó.");
    }} />;
  return <>
    {notice && <p role="status">{notice}</p>}
    <ReportPage key={`${state}:${page}:${revision}`} query={query} type={type} state={state} page={page}
      onState={value => { setState(value); setPage(0); }} onPage={setPage} onDetail={setSelection} />
    {selection && <Dialog title="Detalle de asistencia" description={selection.persona} onClose={() => setSelection(null)}>
      <dl className={styles.detail}>
        <dt>Fecha</dt><dd>{dateLabel(selection.fecha)}</dd>
        <dt>Estado vigente</dt><dd>{LABELS[selection.estado] || selection.estado}</dd>
        <dt>{type === "DOCENTES" ? "Hora de marcación" : "Hora de registro"}</dt><dd>{selection.hora?.slice(0, 8) || "Sin marcación"}</dd>
        {selection.curso && <><dt>Curso</dt><dd>{selection.curso}</dd></>}
        {selection.seccion && <><dt>Sección</dt><dd>{selection.seccion}</dd></>}
        <dt>Origen</dt><dd>{selection.origen === "JORNADA" ? "Jornada programada que cerró sin una marcación válida" : "Registro vigente de asistencia"}</dd>
      </dl>
      {type === "ESTUDIANTES" && selection.sesionId != null && <LastAttendanceCorrection
        key={`${selection.sesionId}:${selection.id}`} sessionId={selection.sesionId} attendanceId={selection.id} />}
      {type === "DOCENTES" && selection.origen === "REGISTRO" && <TeacherMarkingCorrection key={selection.id}
        markingId={selection.id} canCorrect={canCorrect} onSaved={() => {
          setSelection(null); setPage(0); setRevision(n => n + 1);
          setNotice("Corrección de la marcación guardada. El reporte se actualizó.");
        }} />}
      {canCorrect && type === "ESTUDIANTES" && selection.sesionId != null && <div className="form-actions">
        <button className="primary-action" onClick={() => {
          if (selection.sesionId === null) return;
          setCorrectionTarget({ sessionId: selection.sesionId, attendanceId: selection.id });
          setSelection(null); setNotice(""); onEditingChange(true);
        }}>Corregir asistencia</button>
      </div>}
    </Dialog>}
  </>;
}

function ReportPage({ query, type, state, page, onState, onPage, onDetail }: {
  query: string; type: ReportType; state: ReportState | ""; page: number;
  onState: (state: ReportState | "") => void; onPage: (page: number) => void; onDetail: (row: ReportRow) => void;
}) {
  const [data, setData] = useState<Report | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    let alive = true;
    const params = new URLSearchParams(query);
    params.set("pagina", String(page));
    if (state) params.set("estado", state);
    attendanceReport(params.toString()).then(result => { if (alive) setData(result); })
      .catch(problem => { if (alive) setError(message(problem)); });
    return () => { alive = false; };
  }, [query, state, page]);
  if (error) return <Feedback error={error} />;
  if (!data) return <Loading skeleton="metrics" rows={6} columns={5}>Calculando reporte…</Loading>;
  if (data.total === 0) return <p role="status" className={styles.empty}>Sin registros para los filtros seleccionados.</p>;
  return <>
    <p>Total: <strong>{data.total} registros</strong></p>
    <div className={styles.metrics} aria-label="Indicadores de asistencia">
      {data.indicadores.map(item => <button key={item.estado} type="button" aria-pressed={state === item.estado}
        className={styles.metric} onClick={() => onState(state === item.estado ? "" : item.estado)}>
        <span>{LABELS[item.estado]}</span><strong>{item.cantidad}</strong>
        <span>{Number(item.porcentaje).toLocaleString("es-PE", { maximumFractionDigits: 2 })}%</span>
        <small>Ver registros</small>
      </button>)}
    </div>
    {state && <p>Detalle de {LABELS[state].toLowerCase()} · <button className="secondary-action" onClick={() => onState("")}>Ver todos los estados</button></p>}
    <div className={styles.tableWrap}><table className={styles.table}>
      <caption className={styles.caption}>Registros de {type === "ESTUDIANTES" ? "estudiantes" : "docentes"}</caption>
      <thead><tr><th>Persona</th><th>Fecha</th>{type === "ESTUDIANTES" && <th>Curso y sección</th>}<th>Estado</th><th>{type === "DOCENTES" ? "Hora de marcación" : "Hora de registro"}</th><th>Acciones</th></tr></thead>
      <tbody>{data.registros.map(row => <tr key={`${row.origen}:${row.id}`}>
        <td>{row.persona}</td><td>{dateLabel(row.fecha)}</td>
        {type === "ESTUDIANTES" && <td>{row.curso}<small className={styles.secondary}>{row.seccion}</small></td>}
        <td><StatusPill state={row.estado} label={LABELS[row.estado] || row.estado} tone={row.estado === "PRESENTE" ? "success" : row.estado.startsWith("TARDANZA") ? "warning" : "neutral"} /></td>
        <td>{row.hora?.slice(0, 8) || "—"}</td>
        <td><button className="secondary-action" onClick={() => onDetail(row)} aria-label={`Ver detalle de ${row.persona}`}>Ver detalle</button></td>
      </tr>)}</tbody>
    </table></div>
    {data.registros.length === 0 && <p role="status">No hay registros de este estado.</p>}
    <Pager loading={false} data={{ content: data.registros, page: data.pagina, totalElements: data.totalDetalle,
      totalPages: data.totalPaginas, first: data.pagina === 0, last: data.pagina + 1 >= data.totalPaginas }} setPage={onPage} />
  </>;
}
