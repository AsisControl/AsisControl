"use client";
import { FormEvent, useState } from "react";
import Link from "next/link";
import { Eye } from "@/shared/ui";
import type { useAuth } from "@/core/auth/use-auth";
import { AuthIdentityPanel } from "./auth-identity-panel";
import identityStyles from "./auth-identity-panel.module.css";
export function LoginScreen({
  onLogin,
}: {
  onLogin: ReturnType<typeof useAuth>["login"];
}) {
  const [showPassword, setShowPassword] = useState(false);
  const [username, setUsername] = useState("");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setIsSubmitting(true);
    const form = new FormData(event.currentTarget);
    const identifier = String(form.get("username") ?? "").trim();
    if (!/^\d{8}$/.test(identifier)) {
      setError("Ingresa los 8 dígitos de tu usuario institucional.");
      setIsSubmitting(false);
      return;
    }
    const result = await onLogin({
      username: identifier,
      password: String(form.get("password") ?? ""),
    });
    setIsSubmitting(false);
    if (!result.ok) setError(result.message);
  }

  return (
    <main className={`${identityStyles.screen} ${identityStyles.compactScreen}`}>
      <AuthIdentityPanel />
      <section className="login-form-panel">
        <form className="login-form" onSubmit={login}>
          <h2>Iniciar sesión</h2>
          <p>Ingresa tus credenciales institucionales</p>
          <label>
            Usuario institucional <i>*</i>
            <input
              name="username"
              required
              inputMode="numeric"
              autoComplete="username"
              placeholder="Ej. 20260001"
              value={username}
              onChange={(event) => setUsername(event.target.value.replace(/\D/g, "").slice(0, 8))}
              disabled={isSubmitting}
            />
          </label>
          <label>
            Contraseña <i>*</i>
            <span className="password-field">
              <input
                name="password"
                maxLength={72}
                required
                autoComplete="current-password"
                type={showPassword ? "text" : "password"}
                placeholder="Ingresa tu contraseña"
                disabled={isSubmitting}
              />
              <button
                type="button"
                aria-label={
                  showPassword ? "Ocultar contraseña" : "Mostrar contraseña"
                }
                onClick={() => setShowPassword(!showPassword)}
                disabled={isSubmitting}
              >
                <Eye crossed={showPassword} />
              </button>
            </span>
          </label>
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          <Link className="forgot" href="/recuperar-contrasena">
            ¿Olvidaste tu contraseña?
          </Link>
          <button
            className="login-button"
            type="submit"
            disabled={isSubmitting}
          >
            {isSubmitting ? "Validando acceso…" : "Iniciar sesión"}
          </button>
          <div className="login-help">
            ¿Problemas para acceder? Contacta a la administración del colegio.
          </div>
        </form>
      </section>
    </main>
  );
}
