"use client";
import { CheckboxSelect } from "@/shared/ui/checkbox-select";
import { useEffect, useState, type FormEvent } from "react";
import { fieldErrorsOf, message } from "@/core/api/client";
import { Field, FormDialog, Loading } from "@/shared/ui";
import { courseOptions, PERIODS, sectionOptions } from "./lib/labels";
import {
  actualizarEvaluacion,
  crearEvaluacion,
  listarCompetenciasDeAsignacion,
  obtenerReglaVigente,
} from "./services/evaluation-service";
import { listReportPeriods } from "./services/report-service";
import type { ReportPeriod } from "./report-types";
import type {
  CourseCompetency,
  Evaluation,
  EvaluationGradingRule,
  EvaluationPeriod,
  OwnAssignment,
} from "./types";
import styles from "./evaluations.module.css";

const DEFAULT_TYPE = "PRACTICA_CALIFICADA";
const percentage = new Intl.NumberFormat("es-PE", { style: "percent", maximumFractionDigits: 2 });

function validateDate(value: string, year: number | undefined, periods: ReportPeriod[]) {
  if (!value) return "Indica una fecha de evaluación válida.";
  const yearNumber = Number(value.split("-")[0]);
  if (yearNumber < 1900 || yearNumber > 2100) return "Ingresa un año entre 1900 y 2100.";
  const date = new Date(`${value}T00:00:00Z`);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== value) {
    return "Ingresa una fecha de evaluación válida.";
  }
  if (year !== undefined && yearNumber !== year) return `La fecha debe pertenecer al año académico ${year}.`;
  if (periods.length && !periods.some(({ fechaInicio, fechaFin }) => value >= fechaInicio && value <= fechaFin)) {
    return "La fecha debe estar dentro de un período académico activo de la asignación.";
  }
  return "";
}

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
  /** `grade` indica que el docente quiere pasar a registrar las calificaciones. */
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
  const [calendar, setCalendar] = useState<{ id: number; periods: ReportPeriod[] } | null>(null);
  const [descripcion, setDescripcion] = useState(evaluation?.descripcion ?? "");
  const [tipo, setTipo] = useState(evaluation?.tipoEvaluacion ?? "");
  const [ruleAttempt, setRuleAttempt] = useState(0);
  const [ruleResult, setRuleResult] = useState<{
    assignmentId: number;
    attempt: number;
    rule: EvaluationGradingRule | null;
    error?: string;
  } | null>(null);
  const [available, setAvailable] = useState<{ assignmentId: number; items: CourseCompetency[] } | null>(null);
  const [competencyError, setCompetencyError] = useState<{ assignmentId: number; text: string } | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [permitirEntrega, setPermitirEntrega] = useState(false);
  const assignment = assignments.find(
    (item) => String(item.cursoId) === cursoId && String(item.seccionId) === seccionId,
  );
  const idAsignacionCurso = evaluation?.idAsignacionCurso ?? assignment?.id;
  useEffect(() => {
    if (!idAsignacionCurso) return;
    let active = true;
    listReportPeriods(idAsignacionCurso).then(
      (periods) => { if (active) setCalendar({ id: idAsignacionCurso, periods }); },
      () => { if (active) setCalendar({ id: idAsignacionCurso, periods: [] }); },
    );
    return () => { active = false; };
  }, [idAsignacionCurso]);
  useEffect(() => {
    if (!idAsignacionCurso) return;
    let active = true;
    listarCompetenciasDeAsignacion(idAsignacionCurso)
      .then((items) => {
        if (active) setAvailable({ assignmentId: idAsignacionCurso, items });
      })
      .catch((e) => {
        if (active) setCompetencyError({ assignmentId: idAsignacionCurso, text: message(e) });
      });
    return () => {
      active = false;
    };
  }, [idAsignacionCurso]);
  useEffect(() => {
    if (!idAsignacionCurso) return;
    let active = true;
    obtenerReglaVigente(idAsignacionCurso)
      .then((rule) => {
        if (active) setRuleResult({ assignmentId: idAsignacionCurso, attempt: ruleAttempt, rule });
      })
      .catch((e) => {
        if (active) setRuleResult({ assignmentId: idAsignacionCurso, attempt: ruleAttempt, rule: null, error: message(e) });
      });
    return () => {
      active = false;
    };
  }, [idAsignacionCurso, ruleAttempt]);
  const currentRule = ruleResult?.assignmentId === idAsignacionCurso && ruleResult?.attempt === ruleAttempt
    ? ruleResult : null;
  const rule = currentRule?.rule;
  const component = rule?.componentes.find(item => item.tipoEvaluacion === tipo);
  const legacyType = evaluation && rule && !rule.componentes.some(item => item.tipoEvaluacion === evaluation.tipoEvaluacion)
    ? evaluation.tipoEvaluacion : null;
  const options = available?.assignmentId === idAsignacionCurso ? available?.items ?? null : null;
  const catalogError = competencyError?.assignmentId === idAsignacionCurso ? competencyError?.text : "";
  const unavailable = (evaluation?.competencias ?? []).filter(item =>
    options && competencias.includes(item.id) && !options.some(option => option.competenciaId === item.id));
  const academicYear = assignment?.anioAcademico ?? evaluation?.anioAcademico;
  const currentCalendar = calendar?.id === idAsignacionCurso ? calendar : null;
  const dateError = validateDate(fecha, academicYear, currentCalendar?.periods ?? []);
  function selectCourse(value: string) {
    setCursoId(value);
    setSeccionId("");
    setCompetencias([]);
    resetComponent();
  }
  function resetComponent() {
    setTipo("");
    setRuleResult(null);
    setErrors(current => ({ ...current, tipoEvaluacion: "" }));
  }
  function toggle(id: number) {
    setCompetencias((current) =>
      current.includes(id) ? current.filter((item) => item !== id) : [...current, id],
    );
  }
  async function save(grade: boolean) {
    const found: Record<string, string> = {};
    if (!titulo.trim()) found.titulo = "Escribe el nombre de la evaluación.";
    if (dateError) found.fechaEvaluacion = dateError;
    if (!cursoId) found.curso = "Selecciona el curso.";
    if (!seccionId) found.seccion = "Selecciona la sección.";
    if (!competencias.length) found.competencias = "Selecciona al menos una competencia.";
    else if (!options || unavailable.length) found.competencias = "Revisa las competencias disponibles para esta sección.";
    if (rule && !component && (!legacyType || tipo !== legacyType)) {
      found.tipoEvaluacion = "Selecciona un componente.";
    }
    setErrors(found);
    if (Object.keys(found).length) return;
    if (!idAsignacionCurso) {
      setError("No tienes asignado ese curso en la sección elegida.");
      return;
    }
    if (!currentRule || currentRule.error) return;
    if (!currentCalendar) {
      setError("Espera a que se cargue el calendario académico y vuelve a guardar.");
      return;
    }
    setBusy(true);
    setError("");
    const input = {
      idAsignacionCurso,
      titulo: titulo.trim(),
      descripcion: descripcion.trim() || null,
      tipoEvaluacion: rule ? tipo : evaluation?.tipoEvaluacion ?? DEFAULT_TYPE,
      periodoEvaluacion: periodo,
      fechaEvaluacion: fecha,
      idsCompetencia: competencias,
      ...(!evaluation && permitirEntrega ? { permitirEntrega: true } : {}),
    };
    try {
      onSaved(
        await (evaluation ? actualizarEvaluacion(evaluation.id, input) : crearEvaluacion(input)),
        grade || permitirEntrega,
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
      submitLabel={permitirEntrega ? "Continuar a la tarea" : evaluation ? "Guardar y calificar" : "Crear evaluación"}
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
        min={academicYear ? `${academicYear}-01-01` : "1900-01-01"}
        max={academicYear ? `${academicYear}-12-31` : "2100-12-31"}
        error={(fecha && dateError) || errors.fechaEvaluacion}
        onChange={(event) => {
          setFecha(event.target.value);
          setErrors((current) => ({ ...current, fechaEvaluacion: "" }));
        }}
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
          onChange={(event) => {
            setSeccionId(event.target.value);
            setCompetencias([]);
            setCompetencyError(null);
            resetComponent();
          }}
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
      {idAsignacionCurso && !currentRule && (
        <div className="full-field"><Loading variant="inline">Cargando regla de calificación…</Loading></div>
      )}
      {currentRule?.error && (
        <div className="full-field">
          <p className="field-error" role="alert">No se pudo consultar la regla de calificación. {currentRule.error}</p>
          <button type="button" className={styles.linkButton} onClick={() => setRuleAttempt(attempt => attempt + 1)}>
            Reintentar consulta
          </button>
        </div>
      )}
      {currentRule && !currentRule.error && !rule && (
        <p className="hint full-field">Esta asignación no tiene una regla de calificación vigente.</p>
      )}
      {rule && <>
        <label className="field">
          <span>Componente<span className="field-required"> *</span></span>
          <select
            required
            value={tipo}
            aria-invalid={errors.tipoEvaluacion ? true : undefined}
            onChange={(event) => {
              setTipo(event.target.value);
              setErrors(current => ({ ...current, tipoEvaluacion: "" }));
            }}
          >
            <option value="">— Selecciona —</option>
            {legacyType && <option value={legacyType}>{legacyType} · Fuera de la regla vigente</option>}
            {rule.componentes.map(item => (
              <option key={item.tipoEvaluacion} value={item.tipoEvaluacion}>{item.nombre}</option>
            ))}
          </select>
          {errors.tipoEvaluacion && <small className="field-error" role="alert">{errors.tipoEvaluacion}</small>}
        </label>
        <Field
          label="Peso del componente"
          value={component ? percentage.format(component.peso / 100) : "—"}
          readOnly
          className={styles.componentWeight}
          hint="Definido por las reglas académicas."
        />
      </>}
      {!evaluation && <label className="full-field">
        <input type="checkbox" checked={permitirEntrega} onChange={event => setPermitirEntrega(event.target.checked)} />
        {" "}Permitir entrega del estudiante
      </label>}
      <fieldset className={`full-field ${styles.competencies}`}>
        <legend>
          Competencias a evaluar<span className="field-required"> *</span>
        </legend>
        {!idAsignacionCurso ? (
          <p className="hint">Selecciona un curso y una sección para ver las competencias del plan.</p>
        ) : catalogError ? (
          <p className="field-error" role="alert">{catalogError}</p>
        ) : !options ? (
          <Loading variant="inline">Cargando competencias…</Loading>
        ) : !options.length ? (
          <p className="hint">El plan de este curso aún no tiene competencias disponibles.</p>
        ) : (
          <CheckboxSelect singular="competencia" plural="competencias"
            options={options.map(item => ({ id: item.competenciaId, label: item.competenciaNombre }))}
            selected={competencias} onChange={setCompetencias} />
        )}
        {unavailable.map(item => <label key={item.id}>
          <input type="checkbox" checked onChange={() => toggle(item.id)} />
          {item.nombre} · Retirada del plan
        </label>)}
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
