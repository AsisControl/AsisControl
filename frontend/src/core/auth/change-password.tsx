"use client";
/* eslint-disable @next/next/no-img-element -- SVG assets are served from public/. */
import { useState, type FormEvent } from "react";
import { passwordError, passwordRules } from "./password-policy";
import { api, message } from "@/core/api/client";
import { Feedback, PasswordField } from "@/shared/ui";
import { AccessArrow } from "@/core/layout/access-brand";
import access from "@/core/layout/access.module.css";
import { AccessLayout } from "@/core/layout/access-layout";
import layout from "@/core/layout/login-screen.module.css";
import styles from "./change-password.module.css";
export function ChangePassword({
  onLogout,
  mandatory = true,
}: {
  onLogout: () => Promise<void>;
  mandatory?: boolean;
}) {
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [newPassword, setNewPassword] = useState("");
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setError("");
    if (f.get("newPassword") !== f.get("confirm")) {
      setError("Las contraseñas nuevas no coinciden.");
      return;
    }
    const policy = passwordError(String(f.get("newPassword") || ""));
    if (policy) {
      setError(policy);
      return;
    }
    setBusy(true);
    try {
      await api("/api/auth/change-password", {
        method: "POST",
        body: JSON.stringify({
          currentPassword: f.get("currentPassword"),
          newPassword: f.get("newPassword"),
        }),
      });
      setDone(true);
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  if (done)
    return (
      <AccessLayout titleId="password-title">
        <span className={styles.successIcon} aria-hidden="true">
          <img src="/icons/check.svg" alt="" width={26} height={26} />
        </span>
        <header className={layout.formHeader}>
          <h1 id="password-title">Contraseña actualizada</h1>
          <p>Inicia sesión nuevamente con tu contraseña nueva.</p>
        </header>
        <button className={`${access.primary} ${layout.submit}`} onClick={onLogout}>
          Ir al inicio de sesión
          <AccessArrow />
        </button>
      </AccessLayout>
    );
  return (
    <AccessLayout titleId="password-title">
      <p className={styles.badge}>{mandatory ? "Primer ingreso" : "Seguridad de tu cuenta"}</p>
      <header className={`${layout.formHeader} ${styles.header}`}>
        <h1 id="password-title">{mandatory ? "Crea tu contraseña" : "Cambiar contraseña"}</h1>
        <p>
          {mandatory
            ? "Debes reemplazar tu contraseña temporal para continuar."
            : "Confirma tu contraseña actual y elige una nueva."}
        </p>
      </header>
      <div className={styles.feedback}>
        <Feedback error={error} />
      </div>
      <form className={styles.form} onSubmit={submit}>
        <fieldset className={styles.fields} disabled={busy}>
          <PasswordField
            label="Contraseña actual"
            name="currentPassword"
            required
            autoComplete="current-password"
            placeholder={mandatory ? "Ingresa tu contraseña temporal" : "Ingresa tu contraseña actual"}
          />
          <PasswordField
            label="Nueva contraseña"
            name="newPassword"
            required
            minLength={8}
            maxLength={64}
            autoComplete="new-password"
            placeholder="Mínimo 8 caracteres"
            aria-describedby="password-rules"
            onChange={(event) => setNewPassword(event.currentTarget.value)}
          />
          <ul id="password-rules" className={styles.rules} aria-label="Requisitos de la nueva contraseña">
            {passwordRules.map(({ label, isMet }) => {
              const met = isMet(newPassword);
              return (
                <li key={label} data-met={met}>
                  {label}
                  <span className={styles.ruleState}>{met ? ": cumplido" : ": pendiente"}</span>
                </li>
              );
            })}
          </ul>
          <PasswordField
            label="Confirmar nueva contraseña"
            name="confirm"
            required
            autoComplete="new-password"
            placeholder="Repite la contraseña"
          />
        </fieldset>
        <button className={`${access.primary} ${layout.submit}`} disabled={busy}>
          {busy ? "Guardando…" : "Guardar contraseña"}
          <AccessArrow />
        </button>
        <button
          className={styles.logout}
          type="button"
          disabled={busy}
          onClick={onLogout}
        >
          Cerrar sesión
        </button>
      </form>
    </AccessLayout>
  );
}
