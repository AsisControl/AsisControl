"use client";
import { useEffect, useState } from "react";
import { ApiError, message } from "@/core/api/client";
import { Feedback } from "@/shared/ui";
import { countLabel } from "@/shared/lib/formatters";
import { LEVEL_SCALE, LEVELS } from "./lib/labels";
import { corregirNiveles, guardarNiveles, obtenerPlanilla } from "./services/evaluation-service";
import { StudentProgress } from "./student-progress";
import type { AchievementLevel, GradeCell, GradeSheetData, GradeSheetStudent } from "./types";
import styles from "./evaluations.module.css";

const REASON_MAX = 500;
const cellKey = (idAlumno: number, idCompetencia: number) => `${idAlumno}:${idCompetencia}`;
const noteOf = (student: GradeSheetStudent, idCompetencia: number) =>
  student.notas.find((note) => note.idCompetencia === idCompetencia);

/**
 * Planilla de niveles por estudiante y competencia (US-18). En borrador se registran libremente;
 * publicada entra en modo corrección y cada cambio exige motivo (US-50); después es de consulta.
 */
export function GradeSheet({
  evaluationId,
  competencyId,
  canWrite,
}: {
  evaluationId: number;
  /** Competencia elegida en los filtros; null muestra todas. */
  competencyId: number | null;
  canWrite: boolean;
}) {
  const [sheet, setSheet] = useState<GradeSheetData | null>(null);
  const [loadError, setLoadError] = useState("");
  const [tick, setTick] = useState(0);
  const [changes, setChanges] = useState<Map<string, AchievementLevel>>(new Map());
  const [motivo, setMotivo] = useState("");
  const [motivoError, setMotivoError] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [student, setStudent] = useState<GradeSheetStudent | null>(null);
  useEffect(() => {
    let active = true;
    obtenerPlanilla(evaluationId)
      .then((value) => {
        if (!active) return;
        setSheet(value);
        setLoadError("");
      })
      .catch((e) => {
        if (active) setLoadError(message(e));
      });
    return () => {
      active = false;
    };
  }, [evaluationId, tick]);
  if (loadError) return <Feedback error={loadError} />;
  if (!sheet) return <p role="status">Cargando planilla…</p>;
  const { evaluacion, alumnos } = sheet;
  const draft = evaluacion.estado === "BORRADOR";
  const correction = evaluacion.estado === "PUBLICADA";
  const editable = canWrite && (draft || correction);
  // El backend las entrega como conjunto: sin ordenar, las columnas cambiarían al recargar.
  const competencias = evaluacion.competencias
    .filter((item) => competencyId === null || item.id === competencyId)
    .sort((a, b) => a.codigo.localeCompare(b.codigo));
  function choose(person: GradeSheetStudent, idCompetencia: number, level: AchievementLevel) {
    const key = cellKey(person.idAlumno, idCompetencia);
    const saved = noteOf(person, idCompetencia)?.nivelLogro;
    setSuccess("");
    setChanges((current) => {
      const next = new Map(current);
      if (next.get(key) === level || saved === level) next.delete(key);
      else next.set(key, level);
      return next;
    });
  }
  type Pending = {
    person: GradeSheetStudent;
    competencia: string;
    idCompetencia: number;
    note: GradeCell | undefined;
    level: AchievementLevel;
  };
  const pending: Pending[] = alumnos.flatMap((person) =>
    evaluacion.competencias.flatMap((item) => {
      const level = changes.get(cellKey(person.idAlumno, item.id));
      return level
        ? [{ person, competencia: item.nombre, idCompetencia: item.id, note: noteOf(person, item.id), level }]
        : [];
    }),
  );
  const missing = alumnos.reduce(
    (count, person) =>
      count +
      evaluacion.competencias.filter(
        (item) => !noteOf(person, item.id) && !changes.has(cellKey(person.idAlumno, item.id)),
      ).length,
    0,
  );
  async function save() {
    if (correction && !motivo.trim()) {
      setMotivoError("Indica el motivo de la corrección.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      if (correction) {
        await corregirNiveles(
          motivo.trim(),
          pending.map((item) => ({
            idCalificacion: item.note!.idCalificacion,
            nivelLogro: item.level,
            version: item.note!.version,
          })),
        );
      } else {
        await guardarNiveles(
          pending.map((item) => ({
            idEvaluacion: evaluacion.id,
            idAlumno: item.person.idAlumno,
            idCompetencia: item.idCompetencia,
            nivelLogro: item.level,
          })),
        );
      }
      setChanges(new Map());
      setMotivo("");
      setSuccess(
        correction
          ? `${countLabel(pending.length, "corrección guardada", "correcciones guardadas")}.`
          : "Niveles guardados. Publica la evaluación desde Mis evaluaciones cuando esté completa.",
      );
      setTick((value) => value + 1);
    } catch (e) {
      setError(message(e));
      if (e instanceof ApiError && e.code === "VERSION_DESACTUALIZADA") {
        setChanges(new Map());
        setTick((value) => value + 1);
      }
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className={styles.sheetLayout}>
      <div className={styles.sheetColumn}>
        {correction && editable && (
          <p className={styles.notice}>
            Estás modificando una evaluación publicada. Los cambios quedarán registrados con el
            motivo de corrección.
          </p>
        )}
        {!editable && (
          <p className="hint">
            {evaluacion.estado === "CERRADA"
              ? "Evaluación cerrada: consulta de solo lectura."
              : "Consulta de solo lectura."}
          </p>
        )}
        <div className={`panel ${styles.sheet}`}>
          <div className={styles.sheetHeading}>
            <div>
              <h3>
                {evaluacion.titulo} — {evaluacion.curso} · {evaluacion.seccion}
              </h3>
              <p>
                {LEVEL_SCALE} · {countLabel(alumnos.length, "estudiante", "estudiantes")}
              </p>
            </div>
            {editable && (
              <button
                type="button"
                className="primary-action"
                disabled={busy || !pending.length}
                onClick={() => void save()}
              >
                {busy ? "Guardando…" : correction ? "Guardar cambios" : "Guardar evaluación"}
              </button>
            )}
          </div>
          <Feedback error={error} success={success} />
          {draft && editable && (
            <p className="hint">
              {missing
                ? `Faltan ${countLabel(missing, "nivel", "niveles")} para poder publicar.`
                : "Planilla completa."}
            </p>
          )}
          {alumnos.length ? (
            <div className={styles.sheetTable}>
              <table aria-label={`Planilla de ${evaluacion.titulo}`}>
                <thead>
                  <tr>
                    <th scope="col">Estudiante</th>
                    {competencias.map((item) => (
                      <th key={item.id} scope="col">
                        {item.nombre}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {alumnos.map((person) => (
                    <tr key={person.idAlumno} className={student?.idAlumno === person.idAlumno ? styles.selectedRow : undefined}>
                      <th scope="row">
                        <button type="button" className={styles.studentName} onClick={() => setStudent(person)}>
                          {person.nombreCompleto}
                        </button>
                      </th>
                      {competencias.map((item) => {
                        const key = cellKey(person.idAlumno, item.id);
                        const note = noteOf(person, item.id);
                        const value = changes.get(key) ?? note?.nivelLogro;
                        return (
                          <td key={item.id}>
                            <div className={styles.levels} role="group" aria-label={`${person.nombreCompleto}, ${item.nombre}`}>
                              {LEVELS.map((level) => (
                                <button
                                  key={level}
                                  type="button"
                                  aria-pressed={value === level}
                                  aria-label={`${level}: ${person.nombreCompleto}, ${item.nombre}`}
                                  disabled={!editable || busy || (correction && !note)}
                                  className={`${styles.level} ${value === level ? styles.levelActive : ""} ${changes.get(key) === level ? styles.levelChanged : ""}`}
                                  onClick={() => choose(person, item.id, level)}
                                >
                                  {level}
                                </button>
                              ))}
                            </div>
                            {note?.corregida && <small className={styles.corrected}>Corregida</small>}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="empty">La sección no tiene estudiantes matriculados.</p>
          )}
        </div>
      </div>
      <aside className={styles.sidebar}>
        {correction && editable && (
          <>
            <section className="panel" aria-label="Cambios pendientes">
              <h4>Cambios pendientes</h4>
              <p>
                <strong>{countLabel(pending.length, "cambio pendiente", "cambios pendientes")}</strong>
              </p>
              <ul className={styles.changeList}>
                {pending.map((item) => (
                  <li key={cellKey(item.person.idAlumno, item.idCompetencia)}>
                    {item.person.nombreCompleto}
                    <small>
                      {item.competencia}: {item.note?.nivelLogro} → {item.level}
                    </small>
                  </li>
                ))}
              </ul>
            </section>
            <section className="panel" aria-label="Motivo">
              <label className="field">
                <span>
                  Motivo de la corrección<span className="field-required"> *</span>
                </span>
                <textarea
                  rows={4}
                  maxLength={REASON_MAX}
                  value={motivo}
                  disabled={busy}
                  aria-invalid={motivoError ? true : undefined}
                  onChange={(event) => {
                    setMotivo(event.target.value);
                    setMotivoError("");
                  }}
                />
                {motivoError ? (
                  <small className="field-error" role="alert">
                    {motivoError}
                  </small>
                ) : (
                  <small className="field-hint">
                    Obligatorio. Quedará registrado junto con el docente responsable y la fecha.{" "}
                    {motivo.length} / {REASON_MAX}
                  </small>
                )}
              </label>
            </section>
          </>
        )}
        <StudentProgress
          key={student ? `${student.idAlumno}-${tick}` : "vacio"}
          student={student}
          evaluation={evaluacion}
        />
      </aside>
    </div>
  );
}
