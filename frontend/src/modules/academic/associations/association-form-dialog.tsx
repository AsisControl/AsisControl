"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Field, FormDialog } from "@/shared/ui";
import type { CourseCompetency } from "../types";
import {
  actualizarAsociacion,
  crearAsociacion,
} from "./services/asociacionService";
export type Option = { id: number; codigo: string; nombre: string };
/** Asocia una competencia a un curso o cambia esa asociación (curso, competencia u orden). */
export function AssociationFormDialog({
  association,
  associations,
  courses,
  competencies,
  initialCourseId,
  onClose,
  onSaved,
}: {
  /** Si viene, se edita esa asociación; si no, se crea una nueva. */
  association?: CourseCompetency;
  /** Asociaciones vigentes: evitan repetir una competencia en el mismo curso. */
  associations: CourseCompetency[];
  courses: Option[];
  competencies: Option[];
  /** Curso elegido en el filtro de la tabla, para crear directamente en él. */
  initialCourseId?: number;
  onClose: () => void;
  onSaved: (association: CourseCompetency) => void;
}) {
  const [courseId, setCourseId] = useState(
    association?.cursoId ?? initialCourseId ?? 0,
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const ofCourse = associations.filter(
    (item) => item.cursoId === courseId && item.id !== association?.id,
  );
  const taken = new Set(ofCourse.map((item) => item.competenciaId));
  const available = competencies.filter((item) => !taken.has(item.id));
  const nextOrder = Math.max(0, ...ofCourse.map((item) => item.orden)) + 1;
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const input = {
      cursoId: courseId,
      competenciaId: Number(form.get("competenciaId")),
      orden: Number(form.get("orden")),
    };
    setBusy(true);
    setError("");
    try {
      onSaved(
        await (association
          ? actualizarAsociacion(association, input)
          : crearAsociacion(input)),
      );
    } catch (e) {
      setError(message(e));
      setBusy(false);
    }
  }
  return (
    <FormDialog
      title={association ? "Editar asociación" : "Nueva asociación"}
      description={
        association
          ? `${association.cursoNombre} · ${association.competenciaNombre}`
          : "Indica qué competencia trabaja el curso y en qué orden."
      }
      submitLabel={association ? "Guardar cambios" : "Asociar competencia"}
      busy={busy}
      error={error}
      onClose={onClose}
      onSubmit={save}
    >
      <label className="field full-field">
        Curso<span className="field-required"> *</span>
        <select
          name="cursoId"
          required
          value={courseId || ""}
          onChange={(event) => setCourseId(Number(event.target.value))}
        >
          <option value="" disabled>
            Seleccionar curso
          </option>
          {courses.map((course) => (
            <option key={course.id} value={course.id}>
              {course.nombre} · {course.codigo}
            </option>
          ))}
        </select>
      </label>
      <label className="field full-field">
        Competencia<span className="field-required"> *</span>
        <select
          key={courseId}
          name="competenciaId"
          required
          disabled={!courseId}
          defaultValue={association?.competenciaId ?? ""}
        >
          <option value="" disabled>
            {courseId ? "Seleccionar competencia" : "Primero elige el curso"}
          </option>
          {available.map((competency) => (
            <option key={competency.id} value={competency.id}>
              {competency.nombre} · {competency.codigo}
            </option>
          ))}
        </select>
        <small className="field-hint">
          {courseId && !available.length
            ? "El curso ya trabaja todas las competencias activas."
            : "Solo se listan las competencias que el curso aún no trabaja."}
        </small>
      </label>
      <Field
        key={`orden-${courseId}`}
        label="Orden en el curso"
        name="orden"
        type="number"
        required
        min={1}
        step={1}
        defaultValue={association?.orden ?? nextOrder}
        hint="Posición de la competencia dentro del curso (1, 2, 3…)."
      />
    </FormDialog>
  );
}
