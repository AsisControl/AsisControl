"use client";
import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { fieldErrorsOf, message } from "@/core/api/client";
import { Field, FormDialog } from "@/shared/ui";
import { hour, weekDayLabels } from "../lib/labels";
import {
  WEEK_DAYS,
  detectConflicts,
  needsReload,
  serverConflict,
  validateBlock,
  type BlockErrors,
  type BlockFormValues,
  type Conflict,
} from "../lib/timetable";
import type {
  Classroom,
  CourseAssignment,
  ScheduleBlock,
  ScheduleSection,
  WeekDay,
} from "../types";
import { ConflictList } from "./conflict-list";
import {
  actualizarHorario,
  crearHorario,
  listarHorarios,
} from "./services/horarioService";
/** Espera antes de consultar los cruces mientras se escribe. */
const CHECK_DELAY_MS = 250;
/** Resultado de la consulta de cruces para una combinación de docente, aula, día y horas. */
type CheckResult = { key: string; blocks: ScheduleBlock[] | null };
type Check =
  | { state: "idle" | "checking" | "failed" }
  | { state: "done"; blocks: ScheduleBlock[] };
/** Ventana "Nuevo bloque": crea o edita un bloque y recalcula los conflictos antes de guardar. */
export function BlockFormDialog({
  section,
  block,
  assignments,
  assignmentsStatus,
  classrooms,
  sectionBlocks,
  onClose,
  onSaved,
  onStale,
}: {
  section: ScheduleSection;
  /** Si viene, se edita ese bloque; si no, se crea uno nuevo. */
  block?: ScheduleBlock;
  /** Asignaciones activas (curso y docente) de la sección. */
  assignments: CourseAssignment[];
  /** Las asignaciones se consultan de nuevo al abrir: pudieron crearse después de entrar a la pestaña. */
  assignmentsStatus: "loading" | "ready" | "failed";
  /** Aulas habilitadas. */
  classrooms: Classroom[];
  /** Bloques vigentes de la sección. */
  sectionBlocks: ScheduleBlock[];
  onClose: () => void;
  onSaved: (block: ScheduleBlock) => void;
  /** El servidor rechazó por datos desactualizados: la matriz debe recargarse. */
  onStale: () => void;
}) {
  const [values, setValues] = useState<BlockFormValues>({
    asignacionCursoId: block ? String(block.asignacionCursoId) : "",
    aulaId: block ? String(block.aulaId) : "",
    diaSemana: block?.diaSemana ?? "LUNES",
    horaInicio: block ? hour(block.horaInicio) : "",
    horaFin: block ? hour(block.horaFin) : "",
  });
  const [errors, setErrors] = useState<BlockErrors>({});
  const [result, setResult] = useState<CheckResult | null>(null);
  const [rejected, setRejected] = useState<Conflict | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const sending = useRef(false);
  const assignment = assignments.find((a) => String(a.id) === values.asignacionCursoId);
  const classroom = classrooms.find((c) => String(c.id) === values.aulaId);
  const complete = !Object.keys(validateBlock(values)).length;
  const docenteId = assignment?.docenteId;
  const aulaId = classroom?.id;
  // Criterio 6: al cambiar día, hora, aula o curso se consultan los bloques del docente y del aula.
  const checkKey =
    complete && docenteId !== undefined && aulaId !== undefined
      ? [docenteId, aulaId, values.diaSemana, values.horaInicio, values.horaFin].join("|")
      : null;
  useEffect(() => {
    if (checkKey === null || docenteId === undefined || aulaId === undefined) return;
    let active = true;
    const timer = setTimeout(() => {
      const anioAcademico = section.anioAcademico;
      Promise.all([
        listarHorarios({ anioAcademico, docenteId }),
        listarHorarios({ anioAcademico, aulaId }),
      ])
        .then(([byTeacher, byClassroom]) => {
          if (active) setResult({ key: checkKey, blocks: [...byTeacher, ...byClassroom] });
        })
        .catch(() => {
          if (active) setResult({ key: checkKey, blocks: null });
        });
    }, CHECK_DELAY_MS);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [checkKey, docenteId, aulaId, section.anioAcademico]);
  const check: Check =
    checkKey === null
      ? { state: "idle" }
      : result?.key !== checkKey
        ? { state: "checking" }
        : result.blocks
          ? { state: "done", blocks: result.blocks }
          : { state: "failed" };
  const unavailable: Conflict[] = [];
  if (block && values.aulaId === String(block.aulaId) && !classroom)
    unavailable.push({
      kind: "inhabilitado",
      title: "Recurso no disponible",
      detail: `${block.aula} está inhabilitada; elige otra aula.`,
    });
  if (block && values.asignacionCursoId === String(block.asignacionCursoId) && !assignment)
    unavailable.push({
      kind: "inhabilitado",
      title: "Recurso no disponible",
      detail: `La asignación de ${block.curso} con ${block.docente} ya no está activa.`,
    });
  const conflicts =
    complete && assignment && classroom
      ? detectConflicts(
          {
            id: block?.id,
            diaSemana: values.diaSemana as WeekDay,
            horaInicio: values.horaInicio,
            horaFin: values.horaFin,
            aulaId: classroom.id,
            docenteId: assignment.docenteId,
            seccionId: section.id,
            capacidadSeccion: section.capacidadMaxima,
            capacidadAula: classroom.capacidad,
          },
          [...sectionBlocks, ...(check.state === "done" ? check.blocks : [])],
        )
      : [];
  const blocking = [...unavailable, ...conflicts];
  const set = (key: keyof BlockFormValues, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
    setRejected(null);
    setError("");
  };
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (sending.current) return;
    const found = validateBlock(values);
    setErrors(found);
    if (Object.keys(found).length) return;
    if (blocking.length) {
      setError("Resuelve los conflictos antes de confirmar el bloque.");
      return;
    }
    const input = {
      asignacionCursoId: Number(values.asignacionCursoId),
      aulaId: Number(values.aulaId),
      diaSemana: values.diaSemana as WeekDay,
      horaInicio: values.horaInicio,
      horaFin: values.horaFin,
    };
    sending.current = true;
    setBusy(true);
    setError("");
    setRejected(null);
    try {
      onSaved(await (block ? actualizarHorario(block, input) : crearHorario(input)));
    } catch (e) {
      // Criterio 7: otro administrador pudo tomar el recurso; el servidor decide.
      const item = serverConflict(e);
      if (item) {
        setRejected(item);
        setErrors(fieldErrorsOf(e));
        if (needsReload(item)) onStale();
      } else setError(message(e));
      sending.current = false;
      setBusy(false);
    }
  }
  const sectionLabel = `${section.grado}° ${section.nombre} · ${section.anioAcademico}`;
  return (
    <FormDialog
      title={block ? "Editar bloque" : "Nuevo bloque"}
      description={`Sección ${sectionLabel} · aforo máximo ${section.capacidadMaxima}`}
      submitLabel={block ? "Guardar cambios" : "Confirmar bloque"}
      busy={busy}
      error={error}
      onClose={onClose}
      onSubmit={save}
      noValidate
      notes={
        <>
          <ConflictList heading="El servidor rechazó el bloque" conflicts={rejected ? [rejected] : []} />
          <ConflictList heading="Conflictos detectados" conflicts={blocking} />
          {check.state === "checking" && (
            <p className="hint" role="status">
              Verificando cruces de docente y aula…
            </p>
          )}
          {check.state === "failed" && (
            <p className="hint" role="status">
              No se pudieron consultar los horarios del docente y del aula; el
              servidor validará los cruces al confirmar.
            </p>
          )}
          {check.state === "done" && !blocking.length && (
            <p className="action-feedback" role="status">
              Sin conflictos de docente, aula, sección ni aforo.
            </p>
          )}
        </>
      }
    >
      {assignmentsStatus === "failed" && (
        <p className="full-field hint" role="status">
          No se pudieron actualizar las asignaciones docentes. Cierra esta ventana y vuelve a
          abrirla para intentarlo de nuevo.
        </p>
      )}
      {assignmentsStatus === "ready" && !assignments.length && (
        <p className="full-field hint">
          La sección {sectionLabel} no tiene asignaciones docentes activas. Regístralas en{" "}
          <Link href="/horarios/asignaciones-docentes">Asignaciones docentes</Link>, en el año{" "}
          {section.anioAcademico}, para crear bloques.
        </p>
      )}
      <label className="field full-field">
        <span>
          Asignación docente<span className="field-required"> *</span>
        </span>
        <select
          name="asignacionCursoId"
          value={values.asignacionCursoId}
          aria-invalid={errors.asignacionCursoId ? true : undefined}
          onChange={(e) => set("asignacionCursoId", e.target.value)}
        >
          <option value="">
            {assignmentsStatus === "loading" && !assignments.length
              ? "Cargando asignaciones…"
              : "Seleccionar curso y docente"}
          </option>
          {block && !assignment && values.asignacionCursoId === String(block.asignacionCursoId) && (
            <option value={block.asignacionCursoId}>
              {block.curso} · {block.docente} (no disponible)
            </option>
          )}
          {assignments.map((item) => (
            <option key={item.id} value={item.id}>
              {item.curso} · {item.docente}
            </option>
          ))}
        </select>
        {errors.asignacionCursoId && (
          <small className="field-error" role="alert">
            {errors.asignacionCursoId}
          </small>
        )}
      </label>
      <label className="field">
        <span>
          Aula<span className="field-required"> *</span>
        </span>
        <select
          name="aulaId"
          value={values.aulaId}
          aria-invalid={errors.aulaId ? true : undefined}
          onChange={(e) => set("aulaId", e.target.value)}
        >
          <option value="">Seleccionar aula</option>
          {block && !classroom && values.aulaId === String(block.aulaId) && (
            <option value={block.aulaId}>{block.aula} (inhabilitada)</option>
          )}
          {classrooms.map((item) => (
            <option key={item.id} value={item.id}>
              {item.nombre} · {item.capacidad} estudiantes
            </option>
          ))}
        </select>
        {errors.aulaId ? (
          <small className="field-error" role="alert">
            {errors.aulaId}
          </small>
        ) : (
          <small className="field-hint">Solo aulas habilitadas.</small>
        )}
      </label>
      <label className="field">
        <span>
          Día<span className="field-required"> *</span>
        </span>
        <select
          name="diaSemana"
          value={values.diaSemana}
          aria-invalid={errors.diaSemana ? true : undefined}
          onChange={(e) => set("diaSemana", e.target.value)}
        >
          {WEEK_DAYS.map((day) => (
            <option key={day} value={day}>
              {weekDayLabels[day]}
            </option>
          ))}
        </select>
        {errors.diaSemana && (
          <small className="field-error" role="alert">
            {errors.diaSemana}
          </small>
        )}
      </label>
      <Field
        label="Hora de inicio"
        name="horaInicio"
        type="time"
        required
        value={values.horaInicio}
        error={errors.horaInicio}
        onChange={(e) => set("horaInicio", e.target.value)}
      />
      <Field
        label="Hora de fin"
        name="horaFin"
        type="time"
        required
        value={values.horaFin}
        error={errors.horaFin}
        onChange={(e) => set("horaFin", e.target.value)}
      />
    </FormDialog>
  );
}
