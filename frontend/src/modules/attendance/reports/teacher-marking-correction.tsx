// correccion marcacion docente
"use client";
import { useEffect, useId, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Feedback, Loading } from "@/shared/ui";
import { fechaHora } from "../lib";
import { correctTeacherMarking, teacherMarkingHistory, type TeacherMarkingHistory } from "./report-service";
import styles from "./attendance-reports.module.css";

const ESTADOS: Record<string, string> = {
  PRESENTE: "Presente", TARDANZA: "Tardanza", TARDANZA_JUSTIFICADA: "Tardanza justificada",
  INASISTENCIA: "Inasistencia", INASISTENCIA_JUSTIFICADA: "Inasistencia justificada",
};
const hora = (iso: string) => iso.slice(11, 16);

/**
 * Última corrección de una marcación docente y, para el administrador, el formulario para corregir
 * su hora o su estado con motivo. Si solo se cambia la hora, el servidor recalcula el estado.
 */
export function TeacherMarkingCorrection({ markingId, canCorrect, onSaved }: {
  markingId: number; canCorrect: boolean; onSaved: () => void;
}) {
  const [history, setHistory] = useState<TeacherMarkingHistory | null>(null);
  const [error, setError] = useState("");
  const [open, setOpen] = useState(false);
  const [time, setTime] = useState("");
  const [state, setState] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const hintId = useId();
  useEffect(() => {
    let alive = true;
    teacherMarkingHistory(markingId)
      .then(data => { if (alive) { setHistory(data); setTime(hora(data.marcacion.fechaHoraMarcada)); } })
      .catch(problem => { if (alive) setError(message(problem)); });
    return () => { alive = false; };
  }, [markingId]);
  if (!history) return error ? <Feedback error={error} /> : <Loading>Cargando correcciones…</Loading>;
  const { marcacion } = history;
  const last = history.correcciones[0];
  const timeChanged = time !== hora(marcacion.fechaHoraMarcada);
  const changed = timeChanged || (state !== "" && state !== marcacion.estado);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!changed || !reason.trim()) return;
    setBusy(true); setError("");
    try {
      await correctTeacherMarking(markingId, {
        hora: timeChanged ? time : null, estado: state || null, motivo: reason.trim(), version: marcacion.version,
      });
      onSaved();
    } catch (problem) {
      setError(message(problem));
    } finally {
      setBusy(false);
    }
  }

  return <>
    {last && <dl className={`${styles.detail} ${styles.lastCorrection}`}>
      <dt>Última corrección</dt><dd>{fechaHora(last.fechaHora)} · {last.actor}</dd>
      <dt>Valor anterior</dt><dd>{hora(last.horaAnterior)} · {ESTADOS[last.estadoAnterior] || last.estadoAnterior}</dd>
      <dt>Valor nuevo</dt><dd>{hora(last.horaNueva)} · {ESTADOS[last.estadoNuevo] || last.estadoNuevo}</dd>
      <dt>Motivo</dt><dd>{last.motivo}</dd>
    </dl>}
    {canCorrect && !open && <div className="form-actions">
      <button className="primary-action" type="button" onClick={() => setOpen(true)}>Corregir marcación</button>
    </div>}
    {canCorrect && open && <form className={styles.lastCorrection} onSubmit={save} aria-label="Corregir marcación docente">
      <Feedback error={error} />
      <label className="field">Hora de marcación
        <input type="time" value={time} onChange={event => setTime(event.target.value)} required />
      </label>
      <label className="field">Estado
        <select value={state} onChange={event => setState(event.target.value)} aria-describedby={hintId}>
          <option value="">Según la hora (se recalcula)</option>
          <option value="PRESENTE">Presente</option>
          <option value="TARDANZA">Tardanza</option>
          <option value="INASISTENCIA">Inasistencia</option>
        </select>
        <small id={hintId} className={styles.secondary}>Vigente: {ESTADOS[marcacion.estado] || marcacion.estado}. Si solo cambias la hora, el estado se calcula con el límite de puntualidad de su jornada (15 minutos antes de su primera clase).</small>
      </label>
      <label className="field">Motivo de la corrección
        <textarea value={reason} onChange={event => setReason(event.target.value)} maxLength={500} rows={2} required
          placeholder="Ej. Falla del lector QR" />
      </label>
      <div className="form-actions">
        <button className="secondary-action" type="button" onClick={() => { setOpen(false); setError(""); }}>Cancelar</button>
        <button className="primary-action" type="submit" disabled={busy || !changed || !reason.trim()}>Guardar corrección</button>
      </div>
    </form>}
  </>;
}
