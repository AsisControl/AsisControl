"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { PersonFields, personData } from "@/shared/forms/person-fields";
import { Field, Dialog, FormDialog } from "@/shared/ui";
import { specialtyLabel, specialtyOptions } from "@/shared/lib/formatters";
import { registrarDocente } from "./services/registro-service";
import type { Teacher } from "./types";

/** "+ Registrar docente": crea la ficha; el código lo genera el sistema. */
export function TeacherFormDialog({
  onClose,
  onSaved,
}: {
  onClose: () => void;
  onSaved: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState<Teacher | null>(null);
  const [today] = useState(() => new Date().toISOString().slice(0, 10));

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    try {
      setSaved(
        await registrarDocente({
          persona: personData(form),
          fechaIngreso: String(form.get("fechaIngreso") || ""),
          especialidad: String(form.get("especialidad") || ""),
        }),
      );
      onSaved();
    } catch (problem) {
      setError(message(problem));
    } finally {
      setBusy(false);
    }
  }

  if (saved)
    return (
      <Dialog title="Docente registrado" onClose={onClose}>
        <p>
          <strong>{saved.persona.nombreCompleto}</strong> quedó registrado con el código{" "}
          <strong>{saved.codigoDocente}</strong>.
        </p>
        <p>Su acceso al sistema se crea desde Administración, en Usuarios.</p>
        <div className="form-actions">
          <button type="button" className="primary-action" onClick={onClose}>
            Cerrar
          </button>
        </div>
      </Dialog>
    );

  return (
    <FormDialog
      title="Registrar docente"
      description="Datos de identidad y de su puesto en el colegio."
      size="lg"
      busy={busy}
      error={error}
      onClose={onClose}
      onSubmit={submit}
      submitLabel="Registrar docente"
    >
      <PersonFields />
      <Field label="Fecha de ingreso" name="fechaIngreso" type="date" required max={today} />
      <label className="field">
        Especialidad *
        <select name="especialidad" required defaultValue="">
          <option value="" disabled>
            Selecciona una especialidad
          </option>
          {specialtyOptions.map((option) => (
            <option key={option} value={option}>
              {specialtyLabel(option)}
            </option>
          ))}
        </select>
      </label>
    </FormDialog>
  );
}
