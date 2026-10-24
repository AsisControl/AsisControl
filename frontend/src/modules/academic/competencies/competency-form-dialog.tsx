"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { Field, FormDialog } from "@/shared/ui";
import type { Competency } from "../types";
import {
  actualizarCompetencia,
  crearCompetencia,
} from "./services/competenciaService";
/** Alta de una competencia o edición de una existente (mismo formulario). */
export function CompetencyFormDialog({
  competency,
  onClose,
  onSaved,
}: {
  /** Si viene, se edita esa competencia; si no, se crea una nueva. */
  competency?: Competency;
  onClose: () => void;
  onSaved: (competency: Competency) => void;
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
    };
    setBusy(true);
    setError("");
    try {
      onSaved(
        await (competency
          ? actualizarCompetencia(competency, input)
          : crearCompetencia(input)),
      );
    } catch (e) {
      setError(message(e));
      setBusy(false);
    }
  }
  return (
    <FormDialog
      title={competency ? "Editar competencia" : "Nueva competencia"}
      description={
        competency
          ? `${competency.nombre} · ${competency.codigo}`
          : "Agrega una competencia que luego podrás asociar a los cursos."
      }
      submitLabel={competency ? "Guardar cambios" : "Crear competencia"}
      busy={busy}
      error={error}
      onClose={onClose}
      onSubmit={save}
    >
      <Field
        label="Nombre de la competencia"
        name="nombre"
        required
        fullWidth
        maxLength={180}
        placeholder="Ej. Resuelve problemas de cantidad"
        defaultValue={competency?.nombre}
      />
      <Field
        label="Código"
        name="codigo"
        required
        maxLength={30}
        placeholder="Ej. MAT-C1"
        hint="No puede repetirse entre competencias."
        defaultValue={competency?.codigo}
      />
      <label className="field full-field">
        Descripción
        <textarea
          name="descripcion"
          maxLength={700}
          rows={4}
          placeholder="Qué logra el estudiante con esta competencia (opcional)"
          defaultValue={competency?.descripcion ?? ""}
        />
        <small className="field-hint">Opcional. Máximo 700 caracteres.</small>
      </label>
    </FormDialog>
  );
}
