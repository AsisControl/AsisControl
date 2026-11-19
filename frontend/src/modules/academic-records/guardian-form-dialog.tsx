"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { PersonFields, personData } from "@/shared/forms/person-fields";
import { Dialog, FormDialog } from "@/shared/ui";
import { registrarApoderado } from "./services/registro-service";
import type { Guardian } from "./types";

/** "+ Registrar apoderado": crea la ficha; el código lo genera el sistema. */
export function GuardianFormDialog({
  onClose,
  onSaved,
}: {
  onClose: () => void;
  onSaved: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState<Guardian | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      setSaved(await registrarApoderado(personData(new FormData(event.currentTarget))));
      onSaved();
    } catch (problem) {
      setError(message(problem));
    } finally {
      setBusy(false);
    }
  }

  if (saved)
    return (
      <Dialog title="Apoderado registrado" onClose={onClose}>
        <p>
          <strong>{saved.persona.nombreCompleto}</strong> quedó registrado con el código{" "}
          <strong>{saved.codigoApoderado ?? "—"}</strong>.
        </p>
        <p>Vincúlalo a sus estudiantes desde la pestaña Parentescos.</p>
        <div className="form-actions">
          <button type="button" className="primary-action" onClick={onClose}>
            Cerrar
          </button>
        </div>
      </Dialog>
    );

  return (
    <FormDialog
      title="Registrar apoderado"
      description="Datos de identidad del apoderado."
      size="lg"
      busy={busy}
      error={error}
      onClose={onClose}
      onSubmit={submit}
      submitLabel="Registrar apoderado"
    >
      <PersonFields />
    </FormDialog>
  );
}
