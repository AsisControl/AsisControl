"use client";

/* eslint-disable @next/next/no-img-element -- SVG assets are served from public/. */

import { useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { ChangePassword } from "@/core/auth/change-password";
import { useAuth } from "@/core/auth/use-auth";
import type { AuthenticatedUser } from "@/core/auth/types";
import {
  CHANGE_PASSWORD_PATH,
  navFromSegment,
  navPath,
  roleDetails,
  roleLabels,
} from "@/core/navigation/navigation";
import { NavIcon } from "@/core/navigation/nav-icons";
import { Brand } from "./brand";
import { LoginScreen } from "./login-screen";
import { SessionUserProvider } from "./session-user";
import styles from "./app-shell.module.css";

function Dashboard({
  user,
  onLogout,
  children,
}: {
  user: AuthenticatedUser;
  onLogout: () => Promise<void>;
  children: ReactNode;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const [isAccountMenuOpen, setIsAccountMenuOpen] = useState(false);
  const details = roleDetails[user.role];
  const roleLabel = roleLabels[user.role];
  const activeNav = navFromSegment(user.role, pathname.split("/")[1]);

  if (pathname === CHANGE_PASSWORD_PATH)
    return <ChangePassword onLogout={onLogout} mandatory={false} />;
  function go(path: string) {
    setIsAccountMenuOpen(false);
    router.push(path);
  }
  return (
    <main className="role-home">
      <header className={`top-nav ${styles.header}`}>
        <Brand compact />
        <nav className={styles.navBar} aria-label="Navegación principal">
          {details.nav.map((item) => (
            <button
              className={`${styles.navItem} ${activeNav === item ? "selected" : ""}`}
              aria-current={activeNav === item ? "page" : undefined}
              key={item}
              onClick={() => go(navPath(item))}
            >
              <NavIcon label={item} />
              {item}
            </button>
          ))}
        </nav>
        <div className="account">
          <button
            className="account-trigger"
            aria-expanded={isAccountMenuOpen}
            aria-label="Abrir menú de usuario"
            onClick={() => setIsAccountMenuOpen(!isAccountMenuOpen)}
          >
            <span className="account-name">
              <strong>{user.fullName}</strong>
              <small>{roleLabel}</small>
            </span>
            <span className={`avatar avatar-${user.role.toLowerCase()}`}>
              {user.fullName
                .split(" ")
                .filter(Boolean)
                .slice(0, 2)
                .map((n) => n[0])
                .join("")}
            </span>
          </button>
          {isAccountMenuOpen && (
            <div className="account-menu">
              <div className="account-menu-info">
                <strong>{user.fullName}</strong>
                <small>{roleLabel}</small>
              </div>
              <button
                className="logout-option"
                onClick={() => go(CHANGE_PASSWORD_PATH)}
              >
                Cambiar contraseña
              </button>
              <button className="logout-option" onClick={onLogout}>
                <img src="/sign-out.svg" alt="" />
                Cerrar sesión
              </button>
            </div>
          )}
        </div>
      </header>
      <section className="home-content">{children}</section>
    </main>
  );
}

/**
 * Shell de la aplicación: exige sesión y muestra el menú principal.
 * La pantalla visible la decide la URL (ver module-outlet.tsx).
 */
export function AppShell({ children }: { children: ReactNode }) {
  const router = useRouter();
  const { session, isRestoringSession, login, logout } = useAuth();
  async function logoutToHome() {
    await logout();
    router.replace("/");
  }
  if (isRestoringSession)
    return (
      <main className="app-loading" aria-live="polite">
        Cargando sesión…
      </main>
    );
  if (!session) return <LoginScreen onLogin={login} />;
  if (session.user.requiresPasswordChange)
    return <ChangePassword onLogout={logoutToHome} />;
  return (
    <SessionUserProvider value={session.user}>
      <Dashboard user={session.user} onLogout={logoutToHome}>
        {children}
      </Dashboard>
    </SessionUserProvider>
  );
}
