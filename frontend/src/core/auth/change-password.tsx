"use client";
import { useState, type FormEvent } from "react";
import { passwordError, passwordHint } from "./password-policy";
import { api, message } from "@/core/api/client";
import { Feedback, PasswordField } from "@/shared/ui";
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
      <main className="app-loading">
        <article className="panel">
          <h1>Contraseña actualizada</h1>
          <p>Inicia sesión nuevamente con tu contraseña nueva.</p>
          <button className="primary-action" onClick={onLogout}>
            Ir al inicio de sesión
          </button>
        </article>
      </main>
    );
  return (
    <main className="app-loading">
      <form className="panel password-change" onSubmit={submit}>
        <p className="eyebrow">SEGURIDAD</p>
        <h1>Cambiar contraseña</h1>
        <p>
          {mandatory
            ? "Debes reemplazar tu contraseña temporal para continuar."
            : "Confirma tu contraseña actual y elige una nueva."}
        </p>
        <Feedback error={error} />
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
        />
        <PasswordField
          label="Confirmar nueva contraseña"
          name="confirm"
          required
          autoComplete="new-password"
        />
        <p className="hint">{passwordHint}</p>
        <div className="form-actions">
          <button type="button" disabled={busy} onClick={onLogout}>
            Cerrar sesión
          </button>
          <button className="primary-action" disabled={busy}>
            {busy ? "Guardando…" : "Guardar contraseña"}
          </button>
        </div>
      </form>
    </main>
  );
}
