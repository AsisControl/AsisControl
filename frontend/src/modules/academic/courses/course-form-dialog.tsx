"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Field, FormDialog } from "@/shared/ui";
import type { Course } from "../types";
import { actualizarCurso, crearCurso } from "./services/cursoService";
import { CourseContextFields } from "./course-context";
/** Alta de un curso o edición de uno existente (mismo formulario). */
export function CourseFormDialog({
  course,
  onClose,
  onSaved,
}: {
  /** Si viene, se edita ese curso; si no, se crea uno nuevo. */
  course?: Course;
  onClose: () => void;
  onSaved: (course: Course) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const text = (key: string) => String(form.get(key) || "").trim();
    const gradoAcademicoId = Number(form.get("gradoAcademicoId"));
    if (!gradoAcademicoId) {
      setError("Selecciona el año académico y el grado del curso.");
      return;
    }
    const input = {
      codigo: text("codigo"),
      nombre: text("nombre"),
      descripcion: text("descripcion") || null,
      horasSemanales: Number(form.get("horasSemanales")),
      gradoAcademicoId,
    };
    setBusy(true);
    setError("");
    try {
      onSaved(
        await (course ? actualizarCurso(course, input) : crearCurso(input)),
      );
    } catch (e) {
      setError(message(e));
      setBusy(false);
    }
  }
  return (
    <FormDialog
      title={course ? "Editar curso" : "Nuevo curso"}
      description={
        course
          ? `${course.nombre} · ${course.codigo}`
          : "Agrega un curso al plan curricular de un grado y año académico."
      }
      submitLabel={course ? "Guardar cambios" : "Crear curso"}
      busy={busy}
      error={error}
      onClose={onClose}
      onSubmit={save}
    >
      <Field
        label="Nombre del curso"
        name="nombre"
        required
        fullWidth
        maxLength={120}
        placeholder="Ej. Matemática"
        defaultValue={course?.nombre}
      />
      <Field
        label="Código"
        name="codigo"
        required
        maxLength={30}
        placeholder="Ej. MAT-01"
        hint="Único y en mayúsculas, aunque el curso esté inactivo."
        defaultValue={course?.codigo}
      />
      <CourseContextFields course={course} />
      <Field
        label="Horas semanales"
        name="horasSemanales"
        type="number"
        required
        min={1}
        max={40}
        step={1}
        placeholder="Ej. 4"
        hint="Entre 1 y 40 horas."
        defaultValue={course?.horasSemanales}
      />
      <label className="field full-field">
        Descripción
        <textarea
          name="descripcion"
          maxLength={500}
          rows={4}
          placeholder="Propósito del curso y enfoque de aprendizaje (opcional)"
          defaultValue={course?.descripcion ?? ""}
        />
        <small className="field-hint">Opcional. Máximo 500 caracteres.</small>
      </label>
    </FormDialog>
  );
}
