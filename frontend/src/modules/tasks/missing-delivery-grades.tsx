"use client";

import { useState } from "react";
import { Dialog, Feedback } from "@/shared/ui";
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
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const eligible = data.alumnos.filter(item => competencies.every(id => item.competencias.includes(id)));
  const shown = eligible.filter(item => `${item.nombre} ${item.codigo ?? ""}`.toLocaleLowerCase().includes(filter.trim().toLocaleLowerCase()));
  const valid = students.length > 0 && competencies.length > 0 && /^\d{1,2}(?:\.\d{1,10})?$/.test(grade)
    && Number(grade) <= data.maximo;

  async function apply() {
    if (busy || !valid) return;
    setBusy(true);
    setError("");
    try {
      const result = await gradeMissingDeliveries(taskId, students, competencies, grade);
      onSaved(result.calificaciones);
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }

  return <Dialog title="Calificar sin entrega" onClose={onClose} busy={busy}>
    <fieldset disabled={busy} className={styles.missingGradeFields}>
      <legend>Competencias por calificar</legend>
      {data.competencias.map(item => <label className={styles.taskToggle} key={item.id}>
        <input type="checkbox" checked={competencies.includes(item.id)} onChange={event => {
          const next = event.target.checked ? [...competencies, item.id] : competencies.filter(id => id !== item.id);
          setCompetencies(next);
          setStudents(current => current.filter(id => next.every(competency =>
            data.alumnos.find(student => student.id === id)?.competencias.includes(competency))));
        }} /> {item.nombre}
      </label>)}
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
      <label className="field">Calificación (0 a {data.maximo})
        <input type="number" min={0} max={data.maximo} step="any" value={grade} onChange={event => setGrade(event.target.value)} />
      </label>
      <p role="status">{students.length} estudiantes · {competencies.length} competencias · {students.length * competencies.length} notas</p>
    </fieldset>
    <Feedback error={error} />
    <div className={styles.zeroActions}>
      <button type="button" disabled={busy} onClick={onClose}>Cancelar</button>
      <button type="button" className="primary-action" disabled={busy || !valid} onClick={() => void apply()}>Aplicar calificación</button>
    </div>
  </Dialog>;
}
