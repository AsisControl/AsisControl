"use client";
import { useId, useState, type FormEvent } from "react";
import { message } from "@/core/api/client";
import type { SessionUser, UserRole } from "@/core/auth/types";
import { roleLabels } from "@/core/navigation/navigation";
import { Dialog, Feedback } from "@/shared/ui";
import { AccessArrow, AccessBrand } from "./access-brand";
import { SpaceIcon } from "./role-context";
import { roleSpaces } from "./role-space";
import "./role-theme.css";
import access from "./access.module.css";
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
  const id = useId();
  const [selected, setSelected] = useState<UserRole | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const roles = user.availableRoles ?? [];
  const switching = user.role !== null;
  const canSubmit = Boolean(selected && selected !== user.role && roles.includes(selected));
  let submitLabel = canSubmit && selected
    ? `Continuar como ${roleLabels[selected].toLocaleLowerCase("es")}`
    : "Continuar";
  if (switching) submitLabel = "Cambiar rol";
  if (busy) submitLabel = switching ? "Cambiando rol…" : "Ingresando…";

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
    <form className={`${access.surface} ${styles.form}`} onSubmit={submit}>
      <Feedback error={error} />
      <fieldset className={styles.roles} disabled={busy} aria-label="Roles disponibles">
        {roles.map((role) => (
          <label className={styles.role} data-role-palette={role} key={role}>
            <input
              type="radio"
              name="rol"
              value={role}
              aria-labelledby={`${id}-${role}-name`}
              aria-describedby={`${id}-${role}-description${switching ? "" : ` ${id}-${role}-shortcuts`}`}
              checked={selected === role}
              disabled={user.role === role}
              onChange={() => setSelected(role)}
            />
            <span className={styles.icon} aria-hidden="true">
              <SpaceIcon icon={roleSpaces[role].icon} size={switching ? 18 : 26} />
            </span>
            <span className={styles.roleText}>
              <strong id={`${id}-${role}-name`}>{roleLabels[role]}{user.role === role ? " (actual)" : ""}</strong>
              <small id={`${id}-${role}-description`}>{roleSpaces[role].description}</small>
            </span>
            {!switching && (
              <>
                <span className={styles.indicator} aria-hidden="true">
                  <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="m5 12 4 4L19 6" />
                  </svg>
                </span>
                <span id={`${id}-${role}-shortcuts`} className={styles.shortcuts}>
                  {(role === "DOCENTE" ? ["Mis cursos", "Evaluaciones", "Asistencia"] : roleSpaces[role].shortcuts)
                    .map((shortcut) => <span key={shortcut}>{shortcut}</span>)}
                </span>
              </>
            )}
          </label>
        ))}
      </fieldset>
      <div className={styles.actions}>
        {switching && (
          <button type="button" onClick={onClose} disabled={busy}>Cancelar</button>
        )}
        <button className={access.primary} type="submit" disabled={!canSubmit || busy}>
          {submitLabel}
          {!switching && <AccessArrow />}
        </button>
      </div>
    </form>
  );

  if (switching)
    return <Dialog title="Cambiar de rol" onClose={onClose} busy={busy}>{form}</Dialog>;

  const title = roles.length ? "¿Con qué rol quieres ingresar?" : "Cuenta sin roles habilitados";
  const names = user.fullName.trim().split(/\s+/);
  const initials = names.slice(0, 2).map((name) => Array.from(name)[0]).join("").toLocaleUpperCase("es");
  const roleCount = roles.length === 1 ? "1 rol disponible" : `${roles.length} roles disponibles`;

  return (
    <main className={`${access.surface} ${styles.page}`}>
      <div className={styles.masthead}>
        <AccessBrand className={styles.mastheadContent} />
      </div>
      <div className={styles.workspace}>
        <div className={styles.intro}>
          <span className={styles.avatar} aria-hidden="true">{initials}</span>
          {names[0] && <p className={styles.greeting}>Hola, {names[0]}</p>}
          <h1>{title}</h1>
          <p className={styles.description}>
            {roles.length
              ? `Tu cuenta tiene ${roleCount}.${roles.length > 1 ? " Puedes cambiar de rol desde tu menú de usuario." : ""}`
              : "Tu cuenta aún no tiene roles habilitados. Contacta a la administración del colegio."}
          </p>
        </div>
        {roles.length > 0 && form}
        <footer className={styles.account}>
          <span>Sesión iniciada como <strong>{user.fullName}</strong></span>
          <button type="button" onClick={onClose} disabled={busy}>Cerrar sesión</button>
        </footer>
      </div>
    </main>
  );
}
