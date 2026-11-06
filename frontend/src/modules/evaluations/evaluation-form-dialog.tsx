"use client";
import { useEffect, useState, type FormEvent } from "react";
import { fieldErrorsOf, message } from "@/core/api/client";
import { Field, FormDialog } from "@/shared/ui";
import { courseOptions, PERIODS, sectionOptions } from "./lib/labels";
import {
  actualizarEvaluacion,
  crearEvaluacion,
  listarCompetenciasDelCurso,
} from "./services/evaluation-service";
import type {
  CourseCompetency,
  Evaluation,
  EvaluationPeriod,
  OwnAssignment,
} from "./types";
import styles from "./evaluations.module.css";

/** El prototipo no pide tipo ni ponderación; todas las evaluaciones pesan igual en el consolidado. */
const DEFAULT_TYPE = "PRACTICA_CALIFICADA";
const DEFAULT_WEIGHT = 100;

/** Alta o edición de una evaluación en borrador. */
export function EvaluationFormDialog({
  evaluation,
  assignments,
  onClose,
  onSaved,
}: {
  evaluation?: Evaluation;
  assignments: OwnAssignment[];
  onClose: () => void;
  /** `grade` indica que el docente quiere pasar a registrar los niveles. */
  onSaved: (saved: Evaluation, grade: boolean) => void;
}) {
  const [titulo, setTitulo] = useState(evaluation?.titulo ?? "");
  const [periodo, setPeriodo] = useState<EvaluationPeriod>(evaluation?.periodoEvaluacion ?? "BIMESTRAL");
  const [fecha, setFecha] = useState(evaluation?.fechaEvaluacion ?? "");
  const [cursoId, setCursoId] = useState(evaluation ? String(evaluation.idCurso) : "");
  const [seccionId, setSeccionId] = useState(evaluation ? String(evaluation.idSeccion) : "");
  const [competencias, setCompetencias] = useState<number[]>(
    evaluation?.competencias.map((item) => item.id) ?? [],
  );
  const [descripcion, setDescripcion] = useState(evaluation?.descripcion ?? "");
  const [available, setAvailable] = useState<{ cursoId: string; items: CourseCompetency[] } | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!cursoId) return;
    let active = true;
    listarCompetenciasDelCurso(Number(cursoId))
      .then((items) => {
        if (active) setAvailable({ cursoId, items });
      })
      .catch((e) => {
        if (active) setError(message(e));
      });
    return () => {
      active = false;
    };
  }, [cursoId]);
  const options = available?.cursoId === cursoId ? available.items : null;
  function selectCourse(value: string) {
    setCursoId(value);
    setSeccionId("");
    setCompetencias([]);
  }
  function toggle(id: number) {
    setCompetencias((current) =>
      current.includes(id) ? current.filter((item) => item !== id) : [...current, id],
    );
  }
  async function save(grade: boolean) {
    const found: Record<string, string> = {};
    if (!titulo.trim()) found.titulo = "Escribe el nombre de la evaluación.";
    if (!fecha) found.fechaEvaluacion = "Indica la fecha de evaluación.";
    if (!cursoId) found.curso = "Selecciona el curso.";
    if (!seccionId) found.seccion = "Selecciona la sección.";
    if (!competencias.length) found.competencias = "Selecciona al menos una competencia.";
    setErrors(found);
    if (Object.keys(found).length) return;
    const assignment = assignments.find(
      (item) => String(item.cursoId) === cursoId && String(item.seccionId) === seccionId,
    );
    const idAsignacionCurso = assignment?.id ?? evaluation?.idAsignacionCurso;
    if (!idAsignacionCurso) {
      setError("No tienes asignado ese curso en la sección elegida.");
      return;
    }
    setBusy(true);
    setError("");
    const input = {
      idAsignacionCurso,
      titulo: titulo.trim(),
      descripcion: descripcion.trim() || null,
      tipoEvaluacion: evaluation?.tipoEvaluacion ?? DEFAULT_TYPE,
      periodoEvaluacion: periodo,
      fechaEvaluacion: fecha,
      ponderacionPorcentaje: evaluation?.ponderacionPorcentaje ?? DEFAULT_WEIGHT,
      idsCompetencia: competencias,
    };
    try {
      onSaved(
        await (evaluation ? actualizarEvaluacion(evaluation.id, input) : crearEvaluacion(input)),
        grade,
      );
    } catch (e) {
      setError(message(e));
      setErrors(fieldErrorsOf(e));
      setBusy(false);
    }
  }
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void save(true);
  }
  return (
    <FormDialog
      title={evaluation ? "Editar evaluación" : "Nueva evaluación"}
      busy={busy}
      error={error}
      onClose={onClose}
      onSubmit={submit}
      submitLabel={evaluation ? "Guardar y calificar" : "Crear evaluación"}
      noValidate
      notes={
        !evaluation && (
          <button type="button" className={styles.linkButton} disabled={busy} onClick={() => void save(false)}>
            Guardar borrador
          </button>
        )
      }
    >
      <Field
        label="Nombre de la evaluación"
        required
        fullWidth
        maxLength={160}
        placeholder="Ej. Examen parcial"
        value={titulo}
        error={errors.titulo}
        onChange={(event) => setTitulo(event.target.value)}
      />
      <label className="field">
        Período académico
        <select value={periodo} onChange={(event) => setPeriodo(event.target.value as EvaluationPeriod)}>
          {Object.entries(PERIODS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <Field
        label="Fecha de evaluación"
        type="date"
        required
        value={fecha}
        error={errors.fechaEvaluacion}
        onChange={(event) => setFecha(event.target.value)}
      />
      <label className="field">
        <span>
          Curso<span className="field-required"> *</span>
        </span>
        <select
          value={cursoId}
          disabled={!!evaluation}
          aria-invalid={errors.curso ? true : undefined}
          onChange={(event) => selectCourse(event.target.value)}
        >
          <option value="">— Selecciona —</option>
          {courseOptions(assignments).map(([id, label]) => (
            <option key={id} value={id}>
              {label}
            </option>
          ))}
        </select>
        {errors.curso && (
          <small className="field-error" role="alert">
            {errors.curso}
          </small>
        )}
      </label>
      <label className="field">
        <span>
          Sección<span className="field-required"> *</span>
        </span>
        <select
          value={seccionId}
          disabled={!cursoId || !!evaluation}
          aria-invalid={errors.seccion ? true : undefined}
          onChange={(event) => setSeccionId(event.target.value)}
        >
          <option value="">— Selecciona —</option>
          {sectionOptions(assignments, cursoId).map(([id, label]) => (
            <option key={id} value={id}>
              {label}
            </option>
          ))}
        </select>
        {errors.seccion && (
          <small className="field-error" role="alert">
            {errors.seccion}
          </small>
        )}
      </label>
      <fieldset className={`full-field ${styles.competencies}`}>
        <legend>
          Competencias a evaluar<span className="field-required"> *</span>
        </legend>
        {!cursoId ? (
          <p className="hint">Selecciona un curso para ver las competencias disponibles.</p>
        ) : !options ? (
          <p className="hint" role="status">
            Cargando competencias…
          </p>
        ) : !options.length ? (
          <p className="hint">Este curso aún no tiene competencias asociadas.</p>
        ) : (
          options.map((item) => (
            <label key={item.competenciaId}>
              <input
                type="checkbox"
                checked={competencias.includes(item.competenciaId)}
                onChange={() => toggle(item.competenciaId)}
              />
              {item.competenciaNombre}
            </label>
          ))
        )}
        {errors.competencias && (
          <small className="field-error" role="alert">
            {errors.competencias}
          </small>
        )}
      </fieldset>
      <label className="field full-field">
        Descripción / Indicaciones
        <textarea
          rows={3}
          maxLength={1000}
          placeholder="Indicaciones para los estudiantes..."
          value={descripcion}
          onChange={(event) => setDescripcion(event.target.value)}
        />
      </label>
    </FormDialog>
  );
}
