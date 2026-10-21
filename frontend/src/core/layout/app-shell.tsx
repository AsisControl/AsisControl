"use client";

/* eslint-disable @next/next/no-img-element -- SVG assets are served from public/. */

import { useState } from "react";
import { ChangePassword } from "@/core/auth/change-password";
import { useAuth } from "@/core/auth/use-auth";
import type { AuthenticatedUser } from "@/core/auth/types";
import { roleDetails, roleLabels } from "@/core/navigation/navigation";
import { resolveScreen } from "@/core/navigation/module-outlet";
import { Brand } from "./brand";
import { LoginScreen } from "./login-screen";

function Dashboard({
  user,
  onLogout,
  initialNav = "Inicio",
  startCreate = false,
}: {
  user: AuthenticatedUser;
  onLogout: () => Promise<void>;
  initialNav?: string;
  startCreate?: boolean;
}) {
  const [activeNav, setActiveNav] = useState(initialNav);
  const [isAccountMenuOpen, setIsAccountMenuOpen] = useState(false);
  const [changingPassword, setChangingPassword] = useState(false);
  const details = roleDetails[user.role];
  const roleLabel = roleLabels[user.role];

  if (changingPassword)
    return <ChangePassword onLogout={onLogout} mandatory={false} />;
  const screen = resolveScreen(activeNav, { user, startCreate });
  return (
    <main className="role-home">
      <header className="top-nav">
        <Brand compact />
        <nav aria-label="Navegación principal">
          {details.nav.map((item) => (
            <button
              className={activeNav === item ? "selected" : ""}
              key={item}
              onClick={() => setActiveNav(item)}
            >
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
                onClick={() => setChangingPassword(true)}
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
      <section className="home-content">
        {screen ?? (
          <>
            <div className="page-title">
              <div>
                <h1>
                  {activeNav === "Inicio"
                    ? ["DOCENTE", "ESTUDIANTE"].includes(user.role)
                      ? `Bienvenido/a, ${user.fullName}`
                      : details.title
                    : activeNav}
                </h1>
                <p>
                  {new Date().toLocaleDateString("es-PE", {
                    timeZone: "America/Lima",
                    dateStyle: "long",
                  })}
                </p>
              </div>
            </div>
            <article className="role-message">
              <span className="message-mark">✓</span>
              <div>
                <p className="eyebrow">{activeNav.toUpperCase()}</p>
                <h2>Has ingresado como {roleLabel.toLowerCase()}.</h2>
                <p>
                  {activeNav === "Inicio"
                    ? "Selecciona una opción del menú para continuar."
                    : "Este módulo todavía está pendiente de implementación."}
                </p>
              </div>
            </article>
          </>
        )}
      </section>
    </main>
  );
}

export default function AsisApp({
  initialNav,
  startCreate,
}: {
  initialNav?: string;
  startCreate?: boolean;
}) {
  const { session, isRestoringSession, login, logout } = useAuth();
  if (isRestoringSession)
    return (
      <main className="app-loading" aria-live="polite">
        Cargando sesión…
      </main>
    );
  if (!session) return <LoginScreen onLogin={login} />;
  if (session.user.requiresPasswordChange)
    return <ChangePassword onLogout={logout} />;
  return (
    <Dashboard
      user={session.user}
      onLogout={logout}
      initialNav={initialNav}
      startCreate={startCreate}
    />
  );
}
