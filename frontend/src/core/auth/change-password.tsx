"use client";
import { useState, type FormEvent } from "react";
import { passwordError, passwordHint } from "./password-policy";
import { api, message } from "@/core/api/client";
import { Feedback, PasswordField } from "@/shared/ui";
import { AuthFormLayout } from "./auth-form-layout";
import styles from "./auth-form.module.css";
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
      <AuthFormLayout label="Contraseña actualizada" icon="check">
        <h1>Contraseña actualizada</h1>
        <p className={styles.description}>
          Inicia sesión nuevamente con tu contraseña nueva.
        </p>
        <button className={styles.primaryButton} onClick={onLogout}>
          Ir al inicio de sesión
        </button>
      </AuthFormLayout>
    );
  return (
    <AuthFormLayout label="Cambio de contraseña" icon="lock">
      <h1>Cambiar contraseña</h1>
      <p className={styles.description}>
        {mandatory
          ? "Debes reemplazar tu contraseña temporal para continuar."
          : "Confirma tu contraseña actual y elige una nueva."}
      </p>
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
          />
          <PasswordField
            label="Nueva contraseña"
            name="newPassword"
            required
            minLength={8}
            maxLength={64}
            autoComplete="new-password"
            placeholder="Mínimo 8 caracteres"
          />
          <PasswordField
            label="Confirmar nueva contraseña"
            name="confirm"
            required
            autoComplete="new-password"
            placeholder="Repite la contraseña"
          />
        </fieldset>
        <p className="hint">{passwordHint}</p>
        <button className={styles.primaryButton} disabled={busy}>
          {busy ? "Guardando…" : "Guardar contraseña"}
        </button>
        <button
          className={styles.secondaryButton}
          type="button"
          disabled={busy}
          onClick={onLogout}
        >
          Cerrar sesión
        </button>
      </form>
    </AuthFormLayout>
  );
}
