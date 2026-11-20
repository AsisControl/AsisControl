"use client";
import { useState, type FormEvent } from "react";
import Image from "next/image";
import Link from "next/link";
import { Eye } from "@/shared/ui";
import type { useAuth } from "@/core/auth/use-auth";
import { AccessArrow, AccessBrand } from "./access-brand";
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
    const identifier = String(form.get("username") ?? "").trim();
    if (!/^\d{8}$/.test(identifier)) {
      setError({ message: "Ingresa los 8 dígitos de tu usuario institucional.", field: "username" });
      event.currentTarget.querySelector<HTMLInputElement>('[name="username"]')?.focus();
      return;
    }
    setIsSubmitting(true);
    const result = await onLogin({
      username: identifier,
      password: String(form.get("password") ?? ""),
    });
    setIsSubmitting(false);
    if (!result.ok) setError({ message: result.message });
  }

  return (
    <main className={`${access.surface} ${styles.screen}`}>
      <AccessBrand className={styles.masthead} />
      <div className={styles.stage}>
        <aside className={styles.hero} aria-label="Gestión escolar">
          <div className={styles.art} aria-hidden="true">
            <Image src="/brand/registro-escolar.webp" alt="" fill unoptimized loading="eager" />
          </div>
          <div className={styles.story}>
            <p className={styles.headline}>La jornada escolar, <span>en un solo lugar.</span></p>
            <p className={styles.caption}>Matrícula, asistencia, evaluaciones, tareas y pagos.</p>
          </div>
        </aside>
        <section className={styles.formArea} aria-labelledby="login-title">
          <form className={styles.form} onSubmit={login}>
            <header className={styles.formHeader}>
              <h1 id="login-title">Iniciar sesión</h1>
              <p>Accede con tus credenciales del colegio.</p>
            </header>
            <div className={styles.field}>
              <div className={styles.fieldHeader}>
                <label htmlFor="login-username">Usuario institucional</label>
                <span id="login-username-hint" className={styles.hint}>8 dígitos</span>
              </div>
              <input
                id="login-username"
                name="username"
                required
                inputMode="numeric"
                autoComplete="username"
                placeholder="Ej. 20260001"
                aria-describedby={error?.field ? "login-username-hint login-error" : "login-username-hint"}
                aria-invalid={error?.field === "username" || undefined}
                value={username}
                onChange={(event) => {
                  setUsername(event.currentTarget.value.replace(/\D/g, "").slice(0, 8));
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
        </section>
      </div>
    </main>
  );
}
