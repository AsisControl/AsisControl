"use client";
import { useState, type FormEvent } from "react";
import { passwordError, passwordHint } from "./password-policy";
import { api, message } from "@/core/api/client";
import { Feedback, PasswordField } from "@/shared/ui";
import Image from "next/image";
import { AuthIdentityPanel } from "../layout/auth-identity-panel";
import identityStyles from "../layout/auth-identity-panel.module.css";
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
      <main className={identityStyles.screen}>
        <AuthIdentityPanel />
        <section className={styles.content} aria-label="Contraseña actualizada">
        <article className={styles.confirmation}>
          <div className={styles.icon} aria-hidden="true">
            <Image
              src="data:image/svg+xml;base64,PHN2ZyBwcmVzZXJ2ZUFzcGVjdFJhdGlvPSJub25lIiBvdmVyZmxvdz0idmlzaWJsZSIgc3R5bGU9ImRpc3BsYXk6IGJsb2NrOyIgd2lkdGg9IjE3LjYiIGhlaWdodD0iMTcuNiIgdmlld0JveD0iMCAwIDE3LjYgMTcuNiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPGcgaWQ9ImNoZWNrIj4KPHBhdGggaWQ9IlZlY3RvciIgZD0iTTE0LjY2NjEgNC40TDYuNjAwMjIgMTIuNDY2MUwyLjkzMzkyIDguNzk5NjgiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2U9IiMxMzczMzMiLz4KPC9nPgo8L3N2Zz4="
              width={17.6}
              height={17.6}
              alt=""
              unoptimized
            />
          </div>
          <h1>Contraseña actualizada</h1>
          <p>Inicia sesión nuevamente con tu contraseña nueva.</p>
          <button className={styles.button} onClick={onLogout}>
            Ir al inicio de sesión
          </button>
        </article>
        </section>
      </main>
    );
  return (
    <main className={identityStyles.screen}>
      <AuthIdentityPanel />
      <section className={`${styles.content} ${styles.formContent}`} aria-label="Cambio de contraseña">
      <form className={styles.form} onSubmit={submit}>
        <h1>Cambiar contraseña</h1>
        <p className={styles.description}>
          {mandatory
            ? "Debes reemplazar tu contraseña temporal para continuar."
            : "Confirma tu contraseña actual y elige una nueva."}
        </p>
        <div className={styles.feedback}><Feedback error={error} /></div>
        <div className={styles.fields}>
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
        </div>
        <p className={styles.hint}>{passwordHint}</p>
        <div className={styles.actions}>
          <button className={styles.button} disabled={busy}>
            {busy ? "Guardando…" : "Guardar contraseña"}
          </button>
          <button className={styles.secondaryButton} type="button" disabled={busy} onClick={onLogout}>
            Cerrar sesión
          </button>
        </div>
      </form>
      </section>
    </main>
  );
}
