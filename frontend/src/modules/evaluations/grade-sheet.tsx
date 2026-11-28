"use client";
import { formatWeight } from "@/shared/lib/formatters";
import { useEffect, useState } from "react";
import { ApiError, message } from "@/core/api/client";
import { Feedback, Loading } from "@/shared/ui";
import { BulkImportDialog } from "@/modules/bulk-import";
import { countLabel } from "@/shared/lib/formatters";
import { LEVEL_SCALE, LEVELS } from "./lib/labels";
import { corregirNiveles, guardarNiveles, obtenerPlanilla } from "./services/evaluation-service";
import { StudentProgress } from "./student-progress";
import type { AchievementLevel, GradeCell, GradeSheetData, GradeSheetStudent } from "./types";
import styles from "./evaluations.module.css";

const REASON_MAX = 500;
const OBSERVATION_MAX = 1000;
const cellKey = (idAlumno: number, idCompetencia: number) => `${idAlumno}:${idCompetencia}`;
const noteOf = (student: GradeSheetStudent, idCompetencia: number) =>
  student.notas.find((note) => note.idCompetencia === idCompetencia);

/**
 * Planilla de calificaciones por estudiante y competencia (US-18). En borrador se registran libremente;
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
  const [numericChanges, setNumericChanges] = useState<Map<string, string>>(new Map());
  const [observations, setObservations] = useState<Map<string, string>>(new Map());
  const [motivo, setMotivo] = useState("");
  const [motivoError, setMotivoError] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [student, setStudent] = useState<GradeSheetStudent | null>(null);
  const [importing, setImporting] = useState(false);
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
  useEffect(() => {
    if (sheet && /^#alumno-\d+$/.test(window.location.hash)) {
      document.getElementById(window.location.hash.slice(1))?.scrollIntoView?.({ block: "center" });
    }
  }, [sheet]);
  if (loadError) return <Feedback error={loadError} />;
  if (!sheet) return <Loading rows={6} columns={5}>Cargando planilla…</Loading>;
  const { evaluacion, alumnos } = sheet;
  const draft = evaluacion.estado === "BORRADOR";
  const correction = evaluacion.estado === "PUBLICADA";
  const editable = canWrite && (draft || correction)
    && !evaluacion.idTareaVinculada && !evaluacion.entregaPorConfigurar;
  const numericDraft = draft && evaluacion.modeloCualitativo === false && sheet.numeroVersionEscala !== undefined;
  const validGrade = (value: string) => /^\d{1,2}(?:\.\d{1,10})?$/.test(value) && Number(value) <= 20;
  const invalidNumeric = [...numericChanges.values()].some(value => !validGrade(value));
  // El backend las entrega como conjunto: sin ordenar, las columnas cambiarían al recargar.
  const competencias = evaluacion.competencias
    .filter((item) => competencyId === null || item.id === competencyId)
    .sort((a, b) => a.codigo.localeCompare(b.codigo));
  const pesoCompartido = new Map<number, number>();
  for (const item of competencias) {
    const peso = alumnos[0] ? noteOf(alumnos[0], item.id)?.pesoEfectivo : null;
    if (peso != null && alumnos.every(person => noteOf(person, item.id)?.pesoEfectivo === peso)) {
      pesoCompartido.set(item.id, peso);
    }
  }
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
    level?: AchievementLevel;
    numeric?: string;
    observacion: string | null;
  };
  const observationWithoutLevel = draft && alumnos.some((person) =>
    evaluacion.competencias.some((item) => {
      const key = cellKey(person.idAlumno, item.id);
      return Boolean(observations.get(key)?.trim()) && !changes.has(key) && !numericChanges.has(key)
        && !noteOf(person, item.id)?.nivelLogro;
    }),
  );
  const pending: Pending[] = alumnos.flatMap((person) =>
    evaluacion.competencias.flatMap<Pending>((item) => {
      const key = cellKey(person.idAlumno, item.id);
      const note = noteOf(person, item.id);
      const changedLevel = changes.get(key);
      const editedObservation = draft ? observations.get(key)?.trim() : undefined;
      const savedObservation = note?.observacion ?? null;
      const observationChanged = editedObservation !== undefined && editedObservation !== (savedObservation ?? "").trim();
      const level = changedLevel ?? note?.nivelLogro;
      const numeric = numericChanges.get(key);
      const observacion = observationChanged ? editedObservation || null : savedObservation;
      if (numeric !== undefined || (observationChanged && note?.notaNumerica != null)) {
        return [{ person, competencia: item.nombre, idCompetencia: item.id, note,
          numeric: numeric ?? String(note!.notaNumerica), observacion }];
      }
      return level && (changedLevel || observationChanged)
        ? [{ person, competencia: item.nombre, idCompetencia: item.id, note, level,
            observacion }]
        : [];
    }),
  );
  const missing = alumnos.reduce(
    (count, person) =>
      count +
      evaluacion.competencias.filter(
        (item) => !noteOf(person, item.id) && !changes.has(cellKey(person.idAlumno, item.id))
          && !numericChanges.has(cellKey(person.idAlumno, item.id)),
      ).length,
    0,
  );
  async function save() {
    if (invalidNumeric) return;
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
            notaNumerica: item.numeric,
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
            notaNumerica: item.numeric,
            observacion: item.observacion,
          })),
        );
        setSheet(null);
      }
      setChanges(new Map());
      setNumericChanges(new Map());
      setObservations(new Map());
      setMotivo("");
      setSuccess(
        correction
          ? `${countLabel(pending.length, "corrección guardada", "correcciones guardadas")}.`
          : "Calificaciones guardadas. Publica la evaluación desde Mis evaluaciones cuando esté lista.",
      );
      setTick((value) => value + 1);
    } catch (e) {
      setError(message(e));
      if (e instanceof ApiError && e.code === "VERSION_DESACTUALIZADA") {
        setChanges(new Map());
        setNumericChanges(new Map());
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
                {countLabel(alumnos.length, "estudiante", "estudiantes")}
              </p>
              {(numericDraft || alumnos.some(person => person.notas.some(nota => nota.notaNumerica != null)))
                ? <p>Ingresa notas de 0 a 20. Se admiten decimales.</p> : <p>{LEVEL_SCALE}</p>}
              {evaluacion.pesoComponente != null && <p>
                {evaluacion.componente} · Peso del componente: {evaluacion.pesoComponente} %
              </p>}
            </div>
            {editable && (
              <div className={styles.sheetActions}>
                {draft && !numericDraft && (
                  <button
                    type="button"
                    className="secondary-action"
                    disabled={busy || pending.length > 0 || observationWithoutLevel}
                    title={pending.length > 0 || observationWithoutLevel ? "Guarda los cambios antes de la carga masiva." : undefined}
                    onClick={() => setImporting(true)}
                  >
                    Carga masiva
                  </button>
                )}
                <button
                  type="button"
                  className="primary-action"
                  disabled={busy || !pending.length || observationWithoutLevel || invalidNumeric}
                  onClick={() => void save()}
                >
                  {busy ? "Guardando…" : correction ? "Guardar cambios" : "Guardar evaluación"}
                </button>
              </div>
            )}
          </div>
          {importing && (
            <BulkImportDialog
              title="Carga masiva de notas"
              description="Descarga la planilla de esta evaluación: trae a cada estudiante con sus competencias. Completa el nivel de logro (AD, A, B o C)."
              path={`/api/evaluaciones/${evaluationId}/calificaciones/importaciones`}
              templateName={`plantilla_notas_evaluacion_${evaluationId}.xlsx`}
              fileLabel="Archivo Excel de notas"
              onClose={() => setImporting(false)}
              onImported={() => {
                setChanges(new Map());
                setNumericChanges(new Map());
                setObservations(new Map());
                setTick((value) => value + 1);
              }}
            />
          )}
          <Feedback error={error} success={success} />
          {invalidNumeric && <p className="field-error" role="alert">Ingresa notas entre 0 y 20 con hasta 10 decimales.</p>}
          {numericDraft && sheet.numeroVersionEscala === null && <p className="hint">
            Se requiere una escala institucional vigente para guardar notas numéricas.
          </p>}
          {draft && editable && missing > 0 && <p className="hint">Faltan {numericDraft
            ? countLabel(missing, "nota", "notas") : countLabel(missing, "nivel", "niveles")} para publicar.</p>}
          {observationWithoutLevel && <p className="hint">{numericDraft ? "Registra una nota para cada observación pendiente." : "Registra un nivel para cada observación pendiente."}</p>}
          {draft && evaluacion.pesoComponente != null && alumnos.some(person =>
            competencias.some(item => noteOf(person, item.id)?.pesoEfectivo == null)) &&
            <details className={styles.gradeWeightHelp}><summary>¿Cuándo se muestra el peso efectivo?</summary>
              <p>Al publicar la evaluación, según los resultados elegibles de cada competencia.</p>
            </details>}
          {alumnos.length ? (
            <div className={styles.sheetTable}>
              <table aria-label={`Planilla de ${evaluacion.titulo}`}>
                <thead>
                  <tr>
                    <th scope="col">Estudiante</th>
                    {competencias.map((item) => (
                      <th key={item.id} scope="col">
                        {item.nombre}
                        {pesoCompartido.has(item.id) && <small className={styles.institutionalWeightLabel}>
                          Peso efectivo: {formatWeight(pesoCompartido.get(item.id)!)}
                        </small>}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {alumnos.map((person) => (
                    <tr id={`alumno-${person.idAlumno}`} key={person.idAlumno} className={student?.idAlumno === person.idAlumno ? styles.selectedRow : undefined}>
                      <th scope="row">
                        <button type="button" className={styles.studentName} onClick={() => setStudent(person)}>
                          {person.nombreCompleto}
                        </button>
                      </th>
                      {competencias.map((item) => {
                        const key = cellKey(person.idAlumno, item.id);
                        const note = noteOf(person, item.id);
                        const value = changes.get(key) ?? note?.nivelLogro;
                        const numericCell = numericDraft || note?.notaNumerica != null;
                        return (
                          <td key={item.id}>
                            {numericCell ? <>
                              <input className={styles.gradeInput} type="number" min="0" max="20" step="any"
                                  aria-label={`Nota: ${person.nombreCompleto}, ${item.nombre}`}
                                  readOnly={!editable}
                                  value={numericChanges.get(key) ?? note?.notaNumerica ?? ""}
                                  disabled={busy}
                                  onChange={event => {
                                    const nextValue = event.target.value;
                                    setSuccess("");
                                    setNumericChanges(current => {
                                      const next = new Map(current);
                                      const original = note?.notaNumerica;
                                      const unchanged = original == null ? nextValue === ""
                                        : nextValue !== "" && Number(nextValue) === original;
                                      if (unchanged) next.delete(key);
                                      else next.set(key, nextValue);
                                      return next;
                                    });
                                  }} />
                              {!numericChanges.has(key) && note && <small className={styles.gradeMeta}>Nivel {note.nivelLogro}</small>}
                            </> : <div className={styles.levels} role="group" aria-label={`${person.nombreCompleto}, ${item.nombre}`}>
                              {LEVELS.map((level) => (
                                <button
                                  key={level}
                                  type="button"
                                  aria-pressed={value === level}
                                  aria-label={`${level}: ${person.nombreCompleto}, ${item.nombre}`}
                                  disabled={!editable || busy || (correction && !note)}
                                  className={`${styles.level} ${value === level ? styles.levelActive : ""} ${correction && note && note.nivelLogro !== level && changes.get(key) === level ? styles.levelChanged : ""}`}
                                  onClick={() => choose(person, item.id, level)}
                                >
                                  {level}
                                </button>
                              ))}
                            </div>}
                            {note?.corregida && <small className={styles.corrected}>Corregida</small>}
                            {evaluacion.reglaVigente && !pesoCompartido.has(item.id) && (evaluacion.pesoComponente == null || note?.pesoEfectivo != null) &&
                              <small className={styles.corrected}>{evaluacion.pesoComponente == null ? "Sin peso"
                                : note?.pesoEfectivo != null && `Peso efectivo: ${formatWeight(note.pesoEfectivo)}`}</small>}
                            <label className={`field ${styles.gradeObservation}`}>
                              <textarea
                                placeholder={draft && editable ? "Añadir observación" : undefined}
                                aria-label={`Observación: ${person.nombreCompleto}, ${item.nombre}`}
                                rows={2}
                                maxLength={OBSERVATION_MAX}
                                value={observations.get(key) ?? note?.observacion ?? ""}
                                readOnly={!draft || !editable}
                                disabled={busy}
                                onChange={(event) => {
                                  const text = event.target.value;
                                  setSuccess("");
                                  setObservations((current) => new Map(current).set(key, text));
                                }}
                              />
                            </label>
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
                      {item.competencia}: {item.note?.notaNumerica ?? item.note?.nivelLogro} → {item.numeric ?? item.level}
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
