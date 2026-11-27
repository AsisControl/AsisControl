"use client";

import { useState } from "react";
import { Dialog, Feedback } from "@/shared/ui";
import { AchievementButtons, type AchievementChoice } from "@/shared/ui/achievement-buttons";
import { message } from "./task-error-copy";
import { gradeMissingDeliveries, type MissingDeliveries } from "./services/tarea-service";
import styles from "./tasks.module.css";

export function MissingDeliveryGrades({ taskId, data, onClose, onSaved }: {
  taskId: number;
  data: MissingDeliveries;
  onClose: () => void;
  onSaved: (count: number) => void;
}) {
  const [students, setStudents] = useState<number[]>([]);
  const [competencies, setCompetencies] = useState(data.competencias.length === 1 ? [data.competencias[0].id] : []);
  const [filter, setFilter] = useState("");
  const [grade, setGrade] = useState("");
  const [level, setLevel] = useState<AchievementChoice>();
  const [observation, setObservation] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [confirming, setConfirming] = useState(false);
  const eligible = data.alumnos.filter(item => !competencies.length || competencies.some(id => item.competencias.includes(id)));
  const shown = eligible.filter(item => `${item.nombre} ${item.codigo ?? ""}`.toLocaleLowerCase().includes(filter.trim().toLocaleLowerCase()));
  const byCompetency = data.competencias.length > 0;
  const count = byCompetency ? eligible.filter(item => students.includes(item.id))
    .reduce((total, item) => total + competencies.filter(id => item.competencias.includes(id)).length, 0) : students.length;
  const valid = students.length > 0 && (!byCompetency || competencies.length > 0)
    && (data.modeloCualitativo ? Boolean(level) : /^\d{1,4}(?:\.\d{1,10})?$/.test(grade) && Number(grade) <= data.maximo);

  async function apply() {
    if (busy || !valid) return;
    setBusy(true);
    setError("");
    try {
      const value = data.modeloCualitativo && level ? { nivelLogro: level } : { nota: grade };
      const result = await gradeMissingDeliveries(taskId, students, competencies, {
        ...value, ...(observation.trim() ? { observacion: observation.trim() } : {}),
      });
      onSaved(result.calificaciones);
    } catch (failure) {
      setError(message(failure));
      setConfirming(false);
    } finally {
      setBusy(false);
    }
  }

  return <><Dialog title="Calificar sin entrega" onClose={onClose} busy={busy} active={!confirming}>
    <fieldset disabled={busy} className={styles.missingGradeFields}>
      <legend>{byCompetency ? "Competencias por calificar" : "Nota de práctica · Sin peso"}</legend>
      {data.competencias.map(item => <label className={styles.taskToggle} key={item.id}>
        <input type="checkbox" checked={competencies.includes(item.id)} onChange={event => {
          const next = event.target.checked ? [...competencies, item.id] : competencies.filter(id => id !== item.id);
          setCompetencies(next);
          setStudents(current => current.filter(id => !next.length || next.some(competency =>
            data.alumnos.find(student => student.id === id)?.competencias.includes(competency))));
        }} /> {item.nombre}
      </label>)}
      <div className={styles.missingStudentPicker}>
      <label className="field">Buscar estudiante
        <input value={filter} onChange={event => setFilter(event.target.value)} type="search" placeholder="Nombre o código" />
      </label>
      <div className={styles.zeroActions}>
        <button type="button" onClick={() => setStudents(eligible.map(item => item.id))}>Seleccionar todos</button>
        <button type="button" onClick={() => setStudents([])}>Limpiar selección</button>
      </div>
      <div className={styles.missingGradeList}>{shown.map(item => <label key={item.id} className={styles.taskToggle}>
        <input type="checkbox" checked={students.includes(item.id)} onChange={event =>
          setStudents(current => event.target.checked ? [...current, item.id] : current.filter(id => id !== item.id))} />
        <span>{item.nombre}{item.codigo && <small> · {item.codigo}</small>}</span>
      </label>)}</div>
      {!shown.length && <p className="hint">No hay estudiantes disponibles para esta selección.</p>}
      </div>
      {data.modeloCualitativo ? <AchievementButtons label="Nivel para la selección" selected={level} onChange={setLevel} />
        : <label className="field">Calificación (0 a {data.maximo})
        <input type="number" min={0} max={data.maximo} step="any" value={grade} onChange={event => setGrade(event.target.value)} />
      </label>}
      <label className="field">Observación opcional
        <textarea rows={2} maxLength={1000} value={observation}
          onChange={event => setObservation(event.target.value)} />
        <small>Se aplicará a todas las calificaciones seleccionadas.</small>
      </label>
      <p role="status">{students.length} estudiantes{byCompetency ? ` · ${competencies.length} competencias` : ""} · {count} notas</p>
    </fieldset>
    <Feedback error={error} />
    <div className={styles.zeroActions}>
      <button type="button" disabled={busy} onClick={onClose}>Cancelar</button>
      <button type="button" className="primary-action" disabled={busy || !valid} onClick={() => setConfirming(true)}>Aplicar calificación</button>
    </div>
  </Dialog>
  {confirming && <Dialog title="Confirmar calificación sin entrega" role="alertdialog" busy={busy} onClose={() => setConfirming(false)}>
    <p>Se registrarán {count} calificaciones pendientes para {students.length} estudiantes.</p>
    <div className="form-actions">
      <button type="button" disabled={busy} onClick={() => setConfirming(false)}>Volver</button>
      <button type="button" className="primary-action" disabled={busy} onClick={() => void apply()}>Confirmar calificación</button>
    </div>
  </Dialog>}</>;
}
