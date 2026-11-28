"use client";
import { useState, type FormEvent } from "react";
import Link from "next/link";
import { Eye } from "@/shared/ui";
import type { useAuth } from "@/core/auth/use-auth";
import { AccessArrow } from "./access-brand";
import { AccessLayout } from "./access-layout";
import access from "./access.module.css";
import styles from "./login-screen.module.css";
export function LoginScreen({
  onLogin,
}: {
  onLogin: ReturnType<typeof useAuth>["login"];
}) {
  const [showPassword, setShowPassword] = useState(false);
  const [username, setUsername] = useState("");
  const [error, setError] = useState<{ message: string; field?: "username" } | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSubmitting) return;
    setError(null);
    const form = new FormData(event.currentTarget);
    setIsSubmitting(true);
    const result = await onLogin({
      username: String(form.get("username") ?? "").trim(),
      password: String(form.get("password") ?? ""),
    });
    setIsSubmitting(false);
    if (!result.ok) setError({ message: result.message });
  }

  return (
    <AccessLayout titleId="login-title">
      <form className={styles.form} onSubmit={login}>
        <header className={styles.formHeader}>
          <h1 id="login-title">Iniciar sesión</h1>
          <p>Accede con tus credenciales del colegio.</p>
        </header>
        <div className={styles.field}>
          <div className={styles.fieldHeader}>
            <label htmlFor="login-username">Usuario o correo</label>
            <span id="login-username-hint" className={styles.hint}>Código, usuario o correo</span>
          </div>
          <input
            id="login-username"
            name="username"
            maxLength={150}
            required
            autoComplete="username"
            placeholder="Código, usuario o correo"
            aria-describedby={error?.field ? "login-username-hint login-error" : "login-username-hint"}
            aria-invalid={error?.field === "username" || undefined}
            value={username}
            onChange={(event) => {
              setUsername(event.currentTarget.value);
              if (error?.field === "username") setError(null);
            }}
            disabled={isSubmitting}
          />
        </div>
        <div className={styles.field}>
          <label htmlFor="login-password">Contraseña</label>
          <span className={styles.password}>
            <input
              id="login-password"
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
              onClick={() => setShowPassword((visible) => !visible)}
              disabled={isSubmitting}
            >
              <Eye crossed={showPassword} />
            </button>
          </span>
        </div>
        {error && (
          <p id="login-error" className={styles.error} role="alert">
            {error.message}
          </p>
        )}
        <Link className={styles.forgot} href="/recuperar-contrasena">
          ¿Olvidaste tu contraseña?
        </Link>
        <button
          className={`${access.primary} ${styles.submit}`}
          type="submit"
          disabled={isSubmitting}
        >
          {isSubmitting ? "Validando acceso…" : "Iniciar sesión"}
          <AccessArrow />
        </button>
        <p className={styles.help}>¿Problemas para acceder? Contacta a la administración del colegio.</p>
      </form>
    </AccessLayout>
  );
}
