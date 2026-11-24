"use client";

import { useEffect, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { usePaged } from "@/shared/hooks/use-paged";
import { dateLabel, stateLabel } from "@/shared/lib/formatters";
import { Dialog, Feedback, Loading, Pager, PanelHeading, StatusPill } from "@/shared/ui";
import styles from "../academic.module.css";
import { RiskEvaluationDialog } from "./risk-evaluation-dialog";
import { ALERTS_PATH, changeAlertState, comparison, levels, outcomes, readAlert, states,
  type Alert, type RiskState } from "./risk-service";

export function RiskPanel({ canWrite }: { canWrite: boolean }) {
  const [state, setState] = useState("");
  const [level, setLevel] = useState("");
  const [selected, setSelected] = useState<number | null>(null);
  const [evaluating, setEvaluating] = useState(false);
  const filters = new URLSearchParams();
  if (state) filters.set("estado", state);
  if (level) filters.set("nivel", level);
  const list = usePaged<Alert>(ALERTS_PATH, "", filters.toString());

  return <section className="panel">
    <PanelHeading title="Estudiantes en riesgo" summary="Consulta los indicadores y el seguimiento de cada alerta."
      actions={canWrite && <button className="primary-action" onClick={() => setEvaluating(true)}>Evaluar riesgo</button>} />
    <div className="form-grid">
      <label className="field">Estado
        <select value={state} onChange={(event) => { list.setPage(0); setState(event.target.value); }}>
          <option value="">Todos los estados</option>
          {Object.entries(states).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
      <label className="field">Nivel
        <select value={level} onChange={(event) => { list.setPage(0); setLevel(event.target.value); }}>
          <option value="">Todos los niveles</option>
          {Object.entries(levels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
    </div>
    <Feedback error={list.error} />
    {list.loading ? <Loading>Cargando alertas…</Loading> : !list.error && (
      list.data?.content.length ? <>
        <div className={styles.tableWrap}>
          <table className="user-table">
            <thead><tr>{["Estudiante", "Período", "Indicador", "Nivel", "Estado", "Acciones"].map((column) =>
              <th key={column} scope="col">{column}</th>)}</tr></thead>
            <tbody>{list.data.content.map((alert) => <tr key={alert.id}>
              <td>{alert.nombreAlumno}</td><td>{alert.periodo ?? "—"}</td><td>{stateLabel(alert.indicador)}</td>
              <td>{levels[alert.nivelRiesgo]}</td><td><StatusPill state={alert.estado} label={states[alert.estado]} /></td>
              <td className={styles.actions}><button type="button" className={styles.detailButton} aria-label={`Ver alerta de ${alert.nombreAlumno}: ${alert.nombreCriterio}`}
                onClick={() => setSelected(alert.id)}>Ver detalle</button></td>
            </tr>)}</tbody>
          </table>
        </div>
        <Pager data={list.data} loading={list.loading} setPage={list.setPage} />
      </> : <p>No se encontraron alertas con estos filtros.</p>
    )}
    {selected !== null && <RiskDetail id={selected} canWrite={canWrite}
      onClose={() => setSelected(null)} onChange={list.refresh} />}
    {evaluating && <RiskEvaluationDialog onClose={() => setEvaluating(false)} onEvaluated={list.refresh} />}
  </section>;
}

function RiskDetail({ id, canWrite, onClose, onChange }: {
  id: number; canWrite: boolean; onClose: () => void; onChange: () => void;
}) {
  const [detail, setDetail] = useState<Awaited<ReturnType<typeof readAlert>> | null>(null);
  const [state, setState] = useState<RiskState | "">("");
  const [observation, setObservation] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    let active = true;
    readAlert(id).then((result) => { if (active) setDetail(result); })
      .catch((reason) => { if (active) setError(message(reason)); });
    return () => { active = false; };
  }, [id]);
  const alert = detail?.[0];
  const history = detail?.[1] ?? [];
  const allowed = alert?.estado === "ACTIVA" ? ["EN_SEGUIMIENTO", "RESUELTA"] as const :
    alert?.estado === "EN_SEGUIMIENTO" ? ["ACTIVA", "RESUELTA"] as const : [];

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!state || saving || !detail) return;
    if (state === "RESUELTA" && !observation.trim()) {
      setError("Indica una observación para resolver la alerta.");
      return;
    }
    setSaving(true);
    setError("");
    setSuccess("");
    try {
      const updated = await changeAlertState(id, state, observation);
      setDetail([updated, detail[1]]);
      setState("");
      setObservation("");
      setSuccess("Estado actualizado.");
      onChange();
    } catch (reason) { setError(message(reason)); }
    finally { setSaving(false); }
  }

  return <Dialog title="Detalle de alerta" description={alert?.nombreAlumno} busy={saving} size="lg" onClose={onClose}>
    <Feedback error={error} success={success} />
    {!alert && !error && <Loading>Cargando detalle de alerta…</Loading>}
    {alert && <>
      <h3>{alert.nombreCriterio}</h3>
      <p>{stateLabel(alert.indicador)} · Valor detectado: {alert.valorDetectado} · Regla: {comparison(alert.operador, alert.umbral)}</p>
      <p>{alert.mensaje}</p>
      <p>Estado: {states[alert.estado]}{alert.observacion && ` · ${alert.observacion}`}</p>
      <h3>Historial y evidencias</h3>
      {!history.length && <p>No hay evaluaciones registradas para esta alerta.</p>}
      {history.map(({ id: itemId, fecha, evaluacion }) => <div key={itemId}>
        <strong>{dateLabel(fecha)} · {outcomes[evaluacion.resultado]}</strong>
        <p>{evaluacion.motivo}</p>
        {evaluacion.evidencias.length > 0 && <ul>
          {evaluacion.evidencias.map((evidence, index) => <li key={index}>{evidence}</li>)}
        </ul>}
      </div>)}
      {canWrite && allowed.length > 0 && <form className="form-grid" onSubmit={save}>
        <label className="field">Nuevo estado
          <select required value={state} onChange={(event) => setState(event.target.value as RiskState)}>
            <option value="">Selecciona un estado</option>
            {allowed.map((value) => <option key={value} value={value}>{states[value]}</option>)}
          </select>
        </label>
        <label className="field">Observación
          <textarea required={state === "RESUELTA"} maxLength={1000} value={observation}
            onChange={(event) => setObservation(event.target.value)} />
        </label>
        <div><button className="primary-action" disabled={!state || saving}>Guardar cambio</button></div>
      </form>}
    </>}
  </Dialog>;
}
