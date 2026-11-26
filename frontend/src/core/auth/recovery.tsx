"use client";
import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { Feedback, Field, PasswordField } from "@/shared/ui";
import { AccessArrow } from "@/core/layout/access-brand";
import access from "@/core/layout/access.module.css";
import { AccessLayout } from "@/core/layout/access-layout";
import layout from "@/core/layout/login-screen.module.css";
import { passwordError } from "./password-policy";
import { PasswordRulesList } from "./password-rules-list";
import { PasswordSuccessIcon } from "./password-success";
import styles from "./password-screens.module.css";

type RecoveryMode = "request" | "sent" | "validating" | "reset" | "invalid" | "done";

const titles: Record<RecoveryMode, string> = {
  request: "Recuperar contraseña",
  sent: "Revisa tu correo",
  validating: "Verificando enlace",
  reset: "Elige una nueva contraseña",
  invalid: "Enlace no disponible",
  done: "Contraseña actualizada",
};

export function RecoveryScreen() {
  const incoming = useRef("");
  const [token, setToken] = useState("");
  const [mode, setMode] = useState<RecoveryMode>("request");
  const [newPassword, setNewPassword] = useState("");
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
        setNewPassword("");
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
    setNewPassword("");
    setMode("request");
    setNotice("");
    setError("");
  }
  const primary = `${access.primary} ${layout.submit}`;
  return (
    <AccessLayout titleId="recovery-title">
      {mode === "done" ? (
        <PasswordSuccessIcon />
      ) : (
        <p className={styles.badge}>Recupera tu acceso</p>
      )}
      <header className={`${layout.formHeader} ${styles.header}`}>
        <h1 id="recovery-title">{titles[mode]}</h1>
        {mode === "request" && (
          <p>
            Ingresa tu usuario institucional o tu correo de recuperación. Si la
            institución confirmó tu correo, recibirás un enlace válido durante 15
            minutos.
          </p>
        )}
        {mode === "reset" && (
          <p>Debe ser diferente a la actual. Se cerrarán las sesiones anteriores.</p>
        )}
        {mode === "sent" && (
          <p>
            No publiques ni compartas el enlace. Solicitar otro reemplaza al
            anterior. Se permiten hasta tres solicitudes por hora.
          </p>
        )}
        {mode === "validating" && <p role="status">Verificando el enlace…</p>}
      </header>
      <div className={styles.feedback}>
        <Feedback error={error} success={notice} />
      </div>
      {mode === "request" || mode === "reset" ? (
        <form className={styles.form} onSubmit={submit}>
          <fieldset className={styles.fields} disabled={busy}>
            {mode === "request" ? (
              <Field
                label="Usuario institucional o correo de recuperación"
                name="identifier"
                required
                maxLength={150}
                autoComplete="username"
                placeholder="Código, usuario o correo"
              />
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
                  aria-describedby="recovery-password-rules"
                  onChange={(event) => setNewPassword(event.currentTarget.value)}
                />
                <PasswordRulesList id="recovery-password-rules" password={newPassword} />
                <PasswordField
                  label="Confirmar contraseña"
                  name="confirm"
                  required
                  minLength={8}
                  maxLength={64}
                  autoComplete="new-password"
                  placeholder="Repite la contraseña"
                />
              </>
            )}
          </fieldset>
          <button className={primary} disabled={busy}>
            {busy
              ? "Procesando…"
              : mode === "reset"
                ? "Guardar contraseña"
                : "Enviar instrucciones"}
            <AccessArrow />
          </button>
        </form>
      ) : mode === "sent" || mode === "invalid" ? (
        <button className={primary} onClick={again}>
          {mode === "sent" ? "Solicitar otro enlace" : "Solicitar nuevo enlace"}
          <AccessArrow />
        </button>
      ) : mode === "done" ? (
        <Link className={primary} href="/">
          Ir al inicio de sesión
          <AccessArrow />
        </Link>
      ) : null}
      {mode !== "done" && (
        <Link className={styles.backLink} href="/">
          <span aria-hidden="true">←</span> Volver al inicio de sesión
        </Link>
      )}
      <p className={layout.help}>
        Si no tienes acceso al correo o tu contraseña temporal venció, contacta a
        la administración para verificar tu identidad.
      </p>
    </AccessLayout>
  );
}
