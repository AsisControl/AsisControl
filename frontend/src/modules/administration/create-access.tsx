"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { NewAccountCredentials, type AccessCredential } from "@/modules/students";
import { personData } from "@/shared/forms/person-fields";
import { Feedback } from "@/shared/ui";
import { PersonStep, type PersonMode } from "./new-user/person-step";
import styles from "./new-user/new-user.module.css";
import type { PersonChoice } from "./types";
import { registrarAdministrador } from "./users/services/usuarioService";

export function CreateAccess({ onBack }: { onBack: () => void }) {
  const [mode, setMode] = useState<PersonMode>("existing");
  const [person, setPerson] = useState<PersonChoice | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [savedName, setSavedName] = useState("");
  const [credential, setCredential] = useState<AccessCredential | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || (mode === "existing" && !person)) return;
    const data = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    try {
      const saved = await registrarAdministrador(
        mode === "existing" && person
          ? { personaId: person.id }
          : { persona: personData(data) },
      );
      setSavedName(saved.nombreCompleto);
      if (saved.contrasenaTemporal) {
        setCredential({
          row: 0,
          fullName: saved.nombreCompleto,
          role: "ADMINISTRADOR",
          code: null,
          accessCode: saved.codigo,
          username: saved.username,
          email: saved.correo,
          temporaryPassword: saved.contrasenaTemporal,
          expiresAt: saved.contrasenaTemporalExpira,
          existing: false,
        });
      }
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section>
      <button type="button" className={styles.backLink} onClick={onBack} disabled={busy}>
        ← Volver a usuarios
      </button>
      <div className={styles.heading}>
        <h2>Registrar administrador</h2>
        <p>Registra el perfil institucional. Si la persona ya tiene cuenta, conservará sus credenciales.</p>
      </div>
      <Feedback error={error} success={savedName && `Perfil de administrador registrado para ${savedName}.`} />
      {savedName ? (
        <div className="form-actions">
          <button type="button" className="primary-action" onClick={onBack}>Volver a usuarios</button>
        </div>
      ) : (
        <form onSubmit={submit}>
          <fieldset className={styles.steps} disabled={busy}>
            <PersonStep
              mode={mode}
              onModeChange={(next) => { setMode(next); setError(""); }}
              person={person}
              onPersonChange={setPerson}
            />
            <div className="form-actions">
              <button type="button" onClick={onBack}>Cancelar</button>
              <button className="primary-action" disabled={mode === "existing" && !person}>
                {busy ? "Registrando…" : "Registrar perfil"}
              </button>
            </div>
          </fieldset>
        </form>
      )}
      {credential && (
        <NewAccountCredentials credential={credential} onClose={() => setCredential(null)} />
      )}
    </section>
  );
}
