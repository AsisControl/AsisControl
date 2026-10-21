"use client";
import Link from "next/link";
import Image from "next/image";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, message } from "@/core/api/client";
import { Feedback, Field, PasswordField } from "@/shared/ui";
import { passwordError, passwordHint } from "./password-policy";
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
    <main className="recovery-screen">
      <section className="recovery-brand">
        <Image src="/school-logo.svg" width={32} height={32} alt="" />
        <strong>AsisControl</strong>
        <p>Sistema de Gestión Académica</p>
      </section>
      <article className="panel recovery-card">
        <p className="eyebrow">SEGURIDAD DEL ACCESO</p>
        <h1>
          {mode === "reset"
            ? "Elige una nueva contraseña"
            : mode === "done"
              ? "Contraseña actualizada"
              : mode === "invalid"
                ? "Enlace no disponible"
                : "Recuperar contraseña"}
        </h1>
        <Feedback error={error} success={notice} />
        {mode === "validating" ? (
          <p role="status">Verificando el enlace…</p>
        ) : ["request", "reset"].includes(mode) ? (
          <form onSubmit={submit}>
            <fieldset disabled={busy}>
              {mode === "request" ? (
                <>
                  <p>
                    Ingresa el usuario de tu acceso. Si la institución confirmó
                    tu correo, recibirás un enlace válido durante 15 minutos.
                  </p>
                  <Field
                    label="Usuario institucional"
                    name="identifier"
                    required
                    maxLength={150}
                    autoComplete="username"
                  />
                  <p className="hint">
                    Cada acceso de una persona con varios roles se recupera por
                    separado. No creamos cuentas mediante este formulario.
                  </p>
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
                  />
                  <PasswordField
                    label="Confirmar contraseña"
                    name="confirm"
                    required
                    minLength={8}
                    maxLength={64}
                    autoComplete="new-password"
                  />
                  <p className="hint">
                    {passwordHint} Debe ser diferente a la actual. Se cerrarán
                    las sesiones anteriores.
                  </p>
                </>
              )}
              <button className="primary-action" disabled={busy}>
                {busy
                  ? "Procesando…"
                  : mode === "reset"
                    ? "Guardar contraseña"
                    : "Enviar instrucciones"}
              </button>
            </fieldset>
          </form>
        ) : mode === "sent" ? (
          <>
            <p>
              No publiques ni compartas el enlace. Solicitar otro reemplaza al
              anterior. Se permiten hasta tres solicitudes por hora.
            </p>
            <button onClick={again}>Solicitar otro enlace</button>
          </>
        ) : mode === "invalid" ? (
          <button className="primary-action" onClick={again}>
            Solicitar nuevo enlace
          </button>
        ) : null}
        <Link className="back-link" href="/login">
          Volver al inicio de sesión
        </Link>
        <p className="hint">
          Si no tienes acceso al correo o tu contraseña temporal venció,
          contacta a la administración para verificar tu identidad.
        </p>
      </article>
    </main>
  );
}
