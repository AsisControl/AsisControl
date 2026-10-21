"use client";
import Link from "next/link";
import Image from "next/image";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { Feedback, Field, PasswordField } from "@/shared/ui";
import { passwordError, passwordHint } from "./password-policy";
import { RecoveryLayout } from "./recovery-layout";
import styles from "./recovery.module.css";
// Original Figma assets embedded locally to keep the recovery changes self-contained.
const recoveryIcons = {
  shield: "data:image/svg+xml;base64,PHN2ZyBwcmVzZXJ2ZUFzcGVjdFJhdGlvPSJub25lIiBvdmVyZmxvdz0idmlzaWJsZSIgc3R5bGU9ImRpc3BsYXk6IGJsb2NrOyIgd2lkdGg9IjIxLjk5NjciIGhlaWdodD0iMjEuOTk2NyIgdmlld0JveD0iMCAwIDIxLjk5NjcgMjEuOTk2NyIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPGcgaWQ9Ikljb24iIGNsaXAtcGF0aD0idXJsKCNjbGlwMF8wXzQpIj4KPHBhdGggaWQ9IlZlY3RvciIgZD0iTTEwLjk5ODMgMjAuMTYzNkMxMC45OTgzIDIwLjE2MzYgMTguMzMwNiAxNi40OTc1IDE4LjMzMDYgMTAuOTk4NFY0LjU4MjY1TDEwLjk5ODMgMS44MzMwNkwzLjY2NjEyIDQuNTgyNjVWMTAuOTk4NEMzLjY2NjEyIDE2LjQ5NzUgMTAuOTk4MyAyMC4xNjM2IDEwLjk5ODMgMjAuMTYzNloiIHN0cm9rZS13aWR0aD0iMS44MzMwNiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2U9IiMxRTNBOEEiLz4KPC9nPgo8ZGVmcz4KPGNsaXBQYXRoIGlkPSJjbGlwMF8wXzQiPgo8cmVjdCB3aWR0aD0iMjEuOTk2NyIgaGVpZ2h0PSIyMS45OTY3IiBmaWxsPSJ3aGl0ZSIvPgo8L2NsaXBQYXRoPgo8L2RlZnM+Cjwvc3ZnPg==",
  lock: "data:image/svg+xml;base64,PHN2ZyBwcmVzZXJ2ZUFzcGVjdFJhdGlvPSJub25lIiBvdmVyZmxvdz0idmlzaWJsZSIgc3R5bGU9ImRpc3BsYXk6IGJsb2NrOyIgd2lkdGg9IjIxLjk5MjYiIGhlaWdodD0iMjEuOTkyNiIgdmlld0JveD0iMCAwIDIxLjk5MjYgMjEuOTkyNiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPGcgaWQ9Ikljb24iIGNsaXAtcGF0aD0idXJsKCNjbGlwMF8wXzQpIj4KPHBhdGggaWQ9IlZlY3RvciIgZD0iTTQuNTgxNzkgMTAuMDc5OUgxNy40MTA4QzE4LjQyMyAxMC4wNzk5IDE5LjI0MzUgMTAuOTAwNSAxOS4yNDM1IDExLjkxMjdWMTguMzI3MkMxOS4yNDM1IDE5LjMzOTMgMTguNDIzIDIwLjE1OTkgMTcuNDEwOCAyMC4xNTk5SDQuNTgxNzlDMy41Njk2MSAyMC4xNTk5IDIuNzQ5MDcgMTkuMzM5MyAyLjc0OTA3IDE4LjMyNzJWMTEuOTEyN0MyLjc0OTA3IDEwLjkwMDUgMy41Njk2MSAxMC4wNzk5IDQuNTgxNzkgMTAuMDc5OVoiIHN0cm9rZS13aWR0aD0iMS44MzI3MiIgc3Ryb2tlPSIjMUUzQThBIi8+CjxwYXRoIGlkPSJWZWN0b3JfMiIgZD0iTTYuNDE0NTEgMTAuMDc5OVY2LjQxNDUxQzYuNDE0NTEgNS4xOTkzNCA2Ljg5NzIzIDQuMDMzOTQgNy43NTY0OCAzLjE3NDY5QzguNjE1NzQgMi4zMTU0NCA5Ljc4MTEzIDEuODMyNzIgMTAuOTk2MyAxLjgzMjcyQzEyLjIxMTUgMS44MzI3MiAxMy4zNzY5IDIuMzE1NDQgMTQuMjM2MSAzLjE3NDY5QzE1LjA5NTQgNC4wMzM5NCAxNS41NzgxIDUuMTk5MzQgMTUuNTc4MSA2LjQxNDUxVjEwLjA3OTkiIHN0cm9rZS13aWR0aD0iMS44MzI3MiIgc3Ryb2tlPSIjMUUzQThBIi8+CjwvZz4KPGRlZnM+CjxjbGlwUGF0aCBpZD0iY2xpcDBfMF80Ij4KPHJlY3Qgd2lkdGg9IjIxLjk5MjYiIGhlaWdodD0iMjEuOTkyNiIgZmlsbD0id2hpdGUiLz4KPC9jbGlwUGF0aD4KPC9kZWZzPgo8L3N2Zz4=",
  check: "data:image/svg+xml;base64,PHN2ZyBwcmVzZXJ2ZUFzcGVjdFJhdGlvPSJub25lIiBvdmVyZmxvdz0idmlzaWJsZSIgc3R5bGU9ImRpc3BsYXk6IGJsb2NrOyIgd2lkdGg9IjE3LjYiIGhlaWdodD0iMTcuNiIgdmlld0JveD0iMCAwIDE3LjYgMTcuNiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPGcgaWQ9ImNoZWNrIj4KPHBhdGggaWQ9IlZlY3RvciIgZD0iTTE0LjY2NjEgNC40TDYuNjAwMjIgMTIuNDY2MUwyLjkzMzkyIDguNzk5NjgiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2U9IiMxMzczMzMiLz4KPC9nPgo8L3N2Zz4=",
};
export function RecoveryScreen() {
  const incoming = useRef("");
  const [token, setToken] = useState("");
  const [mode, setMode] = useState("request");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  useEffect(() => {
    const value = new URLSearchParams(window.location.hash.slice(1)).get(
      "token",
    );
    if (value) {
      incoming.current = value;
      window.history.replaceState(null, "", window.location.pathname);
    }
    const effective = incoming.current;
    if (!effective) return;
    let active = true;
    // El fragmento no viaja al servidor web; tampoco se conserva en almacenamiento.
    const timer = window.setTimeout(() => {
      setToken(effective);
      setMode("validating");
      api(
        "/api/auth/recovery/validate",
        { method: "POST", body: JSON.stringify({ token: effective }) },
        false,
      )
        .then(() => {
          if (active) setMode("reset");
        })
        .catch((e) => {
          if (active) {
            setMode("invalid");
            setError(message(e));
          }
        });
    }, 0);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, []);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const form = new FormData(event.currentTarget);
    const resetting = mode === "reset";
    const newPassword = String(form.get("newPassword") || "");
    const confirmPassword = String(form.get("confirm") || "");
    if (resetting) {
      const policy = passwordError(newPassword);
      if (policy) {
        setError(policy);
        return;
      }
      if (newPassword !== confirmPassword) {
        setError("Las contraseñas no coinciden.");
        return;
      }
    }
    setBusy(true);
    try {
      const result = await api<{ message: string }>(
        resetting ? "/api/auth/recovery/reset" : "/api/auth/recovery/request",
        {
          method: "POST",
          body: JSON.stringify(
            resetting
              ? { token, newPassword, confirmPassword }
              : { identifier: String(form.get("identifier") || "").trim() },
          ),
        },
        false,
      );
      setNotice(result.message);
      setMode(resetting ? "done" : "sent");
      if (resetting) {
        setToken("");
        incoming.current = "";
      }
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  function again() {
    incoming.current = "";
    setToken("");
    setMode("request");
    setNotice("");
    setError("");
  }
  return (
    <RecoveryLayout showBackLink={mode !== "reset" && mode !== "done"} success={mode === "done"}>
      <article className={mode === "reset" ? styles.reset : mode === "request" ? styles.request : undefined}>
        <div className={`${styles.icon} ${mode === "done" ? styles.successIcon : ""}`} aria-hidden="true">
          <Image
            src={mode === "done" ? recoveryIcons.check : mode === "reset" ? recoveryIcons.lock : recoveryIcons.shield}
            width={mode === "done" ? 17.6 : mode === "reset" ? 21.9926 : 21.9967}
            height={mode === "done" ? 17.6 : mode === "reset" ? 21.9926 : 21.9967}
            alt=""
            unoptimized
          />
        </div>
        <h1>
          {mode === "reset"
            ? "Restablecer contraseña"
            : mode === "done"
              ? "Contraseña restablecida"
              : mode === "invalid"
                ? "Enlace no disponible"
                : mode === "sent" ? "Revisa tu correo" : "Recuperar contraseña"}
        </h1>
        {mode === "request" && <p className={styles.description}>Ingresa el identificador asociado a tu cuenta para continuar.</p>}
        {mode === "reset" && <p className={styles.description}>Ingresa y confirma tu nueva contraseña.</p>}
        {mode === "done" && <p className={styles.description}>Tu contraseña ha sido restablecida con éxito.</p>}
        <div className={styles.feedback}><Feedback error={error} success={mode === "done" ? "" : notice} /></div>
        {mode === "validating" ? (
          <p role="status">Verificando el enlace…</p>
        ) : ["request", "reset"].includes(mode) ? (
          <form className={styles.form} onSubmit={submit}>
            <fieldset disabled={busy}>
              {mode === "request" ? (
                <>
                  <Field
                    label="Identificador de cuenta"
                    name="identifier"
                    required
                    maxLength={150}
                    autoComplete="username"
                    placeholder="Correo corporativo o código institucional"
                  />
                </>
              ) : (
                <>
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
                    label="Confirmar contraseña"
                    name="confirm"
                    required
                    minLength={8}
                    maxLength={64}
                    autoComplete="new-password"
                    placeholder="Repite la contraseña"
                  />
                  <p className="hint">
                    {passwordHint} Debe ser diferente a la actual. Se cerrarán
                    las sesiones anteriores.
                  </p>
                </>
              )}
              <button className={styles.primaryButton} disabled={busy}>
                {busy
                  ? "Procesando…"
                  : mode === "reset"
                    ? "Restablecer contraseña"
                    : "Continuar"}
              </button>
            </fieldset>
          </form>
        ) : mode === "sent" ? (
          <>
            <p>
              No publiques ni compartas el enlace. Solicitar otro reemplaza al
              anterior. Se permiten hasta tres solicitudes por hora.
            </p>
            <button className={styles.primaryButton} onClick={again}>Solicitar otro enlace</button>
          </>
        ) : mode === "invalid" ? (
          <button className={styles.primaryButton} onClick={again}>
            Solicitar nuevo enlace
          </button>
        ) : null}
        {mode === "done" && <Link className={styles.primaryButton} href="/login">
          Volver al inicio de sesión
        </Link>}
      </article>
    </RecoveryLayout>
  );
}
