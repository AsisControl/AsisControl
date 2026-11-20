"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { NewAccountCredentials, type AccessCredential } from "@/modules/students";
import { PersonFields, personData } from "@/shared/forms/person-fields";
import { specialtyLabel } from "@/shared/lib/formatters";
import { Dialog, Feedback, Field } from "@/shared/ui";
import { registrarPerfil, type ProfileRole } from "./services/profile-service";

const specialties = [
  "MATEMATICA", "COMUNICACION", "CIENCIA_Y_TECNOLOGIA", "CIENCIAS_SOCIALES",
  "EDUCACION_FISICA", "INGLES", "ARTE_Y_CULTURA", "TUTORIA",
];

export function RegisterProfile({
  role,
  onRegistered,
}: {
  role: ProfileRole;
  onRegistered: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [credential, setCredential] = useState<AccessCredential | null>(null);
  const title = `Registrar ${role}`;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const data = new FormData(event.currentTarget);
    const teacherFields = role === "docente" ? {
      fechaIngreso: String(data.get("fechaIngreso") ?? ""),
      especialidad: String(data.get("especialidad") ?? ""),
    } : {};
    setBusy(true);
    setError("");
    try {
      const result = await registrarPerfil(role, { persona: personData(data), ...teacherFields });
      setOpen(false);
      setSuccess("Ficha registrada correctamente.");
      const created = result.credencialAcceso;
      if (created?.temporaryPassword) setCredential(created);
      onRegistered();
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <button className="primary-action" type="button" onClick={() => {
        setSuccess("");
        setError("");
        setOpen(true);
      }}>+ {title}</button>
      <Feedback success={success} />
      {open && (
        <Dialog title={title} onClose={() => setOpen(false)} busy={busy} size="lg">
          <form onSubmit={submit}>
            <Feedback error={error} />
            <fieldset disabled={busy}>
              <PersonFields />
              {role === "docente" && (
                <div className="form-grid">
                  <Field label="Fecha de ingreso" name="fechaIngreso" type="date" required />
                  <label className="field">
                    Especialidad *
                    <select name="especialidad" required>
                      <option value="">Selecciona la especialidad</option>
                      {specialties.map((specialty) => (
                        <option key={specialty} value={specialty}>{specialtyLabel(specialty)}</option>
                      ))}
                    </select>
                  </label>
                </div>
              )}
            </fieldset>
            <div className="form-actions">
              <button type="button" disabled={busy} onClick={() => setOpen(false)}>Cancelar</button>
              <button className="primary-action" disabled={busy}>
                {busy ? "Guardando…" : "Guardar ficha"}
              </button>
            </div>
          </form>
        </Dialog>
      )}
      {credential && (
        <NewAccountCredentials credential={credential} onClose={() => setCredential(null)} />
      )}
    </>
  );
}
