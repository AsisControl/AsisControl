/* eslint-disable @next/next/no-img-element -- SVG assets are served from public/. */
import type { ReactNode } from "react";
import { AuthIdentityPanel } from "@/core/layout/auth-identity-panel";
import identityStyles from "@/core/layout/auth-identity-panel.module.css";
import styles from "./auth-form.module.css";
export type AuthFormIcon = "shield" | "lock" | "check";
/** Pantalla de seguridad con la columna institucional y una tarjeta de formulario. */
export function AuthFormLayout({
  label,
  icon,
  backLink,
  children,
}: {
  label: string;
  icon: AuthFormIcon;
  backLink?: ReactNode;
  children: ReactNode;
}) {
  const isSuccess = icon === "check";
  return (
    <main className={identityStyles.screen}>
      <AuthIdentityPanel />
      <section
        className={`${styles.content} ${isSuccess ? styles.successContent : ""}`}
        aria-label={label}
      >
        <div className={styles.card}>
          {backLink}
          <div
            className={`${styles.icon} ${isSuccess ? styles.successIcon : ""}`}
            aria-hidden="true"
          >
            <img src={`/icons/${icon}.svg`} alt="" width={22} height={22} />
          </div>
          {children}
        </div>
      </section>
    </main>
  );
}
