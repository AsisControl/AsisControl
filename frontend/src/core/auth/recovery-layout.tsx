import Link from "next/link";
import type { ReactNode } from "react";
import { AuthIdentityPanel } from "../layout/auth-identity-panel";
import identityStyles from "../layout/auth-identity-panel.module.css";
import styles from "./recovery.module.css";

export function RecoveryLayout({
  children,
  showBackLink = true,
  success = false,
}: {
  children: ReactNode;
  showBackLink?: boolean;
  success?: boolean;
}) {
  return (
    <main className={`${identityStyles.screen} ${styles.screen}`}>
      <AuthIdentityPanel />
      <section className={`${styles.content} ${success ? styles.successContent : ""}`} aria-label="Recuperación de contraseña">
        <div className={styles.card}>
          {showBackLink && <Link className={styles.backLink} href="/login">
            <span aria-hidden="true">←</span> Volver al inicio de sesión
          </Link>}
          {children}
        </div>
      </section>
    </main>
  );
}
