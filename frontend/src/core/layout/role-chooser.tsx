"use client";
import { useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import { AuthFormLayout } from "@/core/auth/auth-form-layout";
import type { SessionUser, UserRole } from "@/core/auth/types";
import { roleLabels } from "@/core/navigation/navigation";
import { Dialog, Feedback } from "@/shared/ui";
import { SpaceIcon } from "./role-context";
import { roleSpaces } from "./role-space";
import styles from "./role-chooser.module.css";

export function RoleChooser({
  user,
  onSelect,
  onClose,
}: {
  user: SessionUser;
  onSelect: (role: UserRole) => Promise<void>;
  onClose: () => void;
}) {
  const [selected, setSelected] = useState<UserRole | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const roles = user.availableRoles ?? [];
  const canSubmit = Boolean(selected && selected !== user.role && roles.includes(selected));
  const title = user.role
    ? "Cambiar de rol"
    : roles.length ? "Elige con qué rol ingresar" : "Cuenta sin roles habilitados";

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected || !canSubmit || busy) return;
    setError("");
    setBusy(true);
    try {
      await onSelect(selected);
    } catch (cause) {
      setError(message(cause));
    } finally {
      setBusy(false);
    }
  }

  const form = (
    <form className={styles.form} onSubmit={submit}>
      <Feedback error={error} />
      <fieldset className={styles.roles} disabled={busy} aria-label="Roles disponibles">
        {roles.map((role) => (
          <label className={styles.role} key={role}>
            <input
              type="radio"
              name="rol"
              value={role}
              checked={selected === role}
              disabled={user.role === role}
              onChange={() => setSelected(role)}
            />
            <span className={styles.icon} aria-hidden="true">
              <SpaceIcon icon={roleSpaces[role].icon} size={18} />
            </span>
            <span>
              <strong>{roleLabels[role]}{user.role === role ? " (actual)" : ""}</strong>
              <small>{roleSpaces[role].description}</small>
            </span>
          </label>
        ))}
      </fieldset>
      <div className={styles.actions}>
        <button type="button" onClick={onClose} disabled={busy}>
          {user.role ? "Cancelar" : "Cerrar sesión"}
        </button>
        <button className="primary-action" disabled={!canSubmit || busy}>
          {busy ? "Cambiando rol…" : user.role ? "Cambiar rol" : "Continuar"}
        </button>
      </div>
    </form>
  );

  if (user.role)
    return <Dialog title={title} onClose={() => { if (!busy) onClose(); }}>{form}</Dialog>;

  return (
    <AuthFormLayout label={title} icon="shield" compact>
      <h1>{title}</h1>
      {roles.length ? form : (
        <>
          <p>Tu cuenta aún no tiene roles habilitados. Contacta a la administración del colegio.</p>
          <button type="button" onClick={onClose}>Cerrar sesión</button>
        </>
      )}
    </AuthFormLayout>
  );
}
