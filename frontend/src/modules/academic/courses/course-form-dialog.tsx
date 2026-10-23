"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Field, FormDialog } from "@/shared/ui";
import type { Course } from "../types";
import { actualizarCurso, crearCurso } from "./services/cursoService";
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
    const input = {
      codigo: text("codigo"),
      nombre: text("nombre"),
      descripcion: text("descripcion") || null,
      horasSemanales: Number(form.get("horasSemanales")),
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
      busy={busy}
      error={error}
      onClose={onClose}
      onSubmit={save}
      notes={
        <p className="hint">
          El código se guarda en mayúsculas y no puede repetirse, aunque el
          curso esté inactivo.
        </p>
      }
    >
      <Field
        label="Código"
        name="codigo"
        required
        maxLength={30}
        placeholder="MAT"
        defaultValue={course?.codigo}
      />
      <Field
        label="Nombre del curso"
        name="nombre"
        required
        maxLength={120}
        placeholder="Matemática"
        defaultValue={course?.nombre}
      />
      <Field
        label="Horas semanales"
        name="horasSemanales"
        type="number"
        required
        min={1}
        max={40}
        step={1}
        defaultValue={course?.horasSemanales}
      />
      <label className="field full-field">
        Descripción
        <textarea
          name="descripcion"
          maxLength={500}
          rows={3}
          defaultValue={course?.descripcion ?? ""}
        />
      </label>
    </FormDialog>
  );
}
