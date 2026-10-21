"use client";

/* eslint-disable @next/next/no-img-element -- SVG assets are served from public/. */

import { Suspense, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { ChangePassword } from "@/core/auth/change-password";
import { useAuth } from "@/core/auth/use-auth";
import type { AuthenticatedUser } from "@/core/auth/types";
import { roleDetails, roleLabels } from "@/core/navigation/navigation";
import { resolveScreen } from "@/core/navigation/module-outlet";
import { canAccess } from "@/core/navigation/access-policy";
import { navigationHref, resolveNavigation } from "@/core/navigation/navigation-routes";
import { Brand } from "./brand";
import { LoginScreen } from "./login-screen";

function Dashboard({
  user,
  onLogout,
}: {
  user: AuthenticatedUser;
  onLogout: () => Promise<void>;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const target = resolveNavigation(pathname, searchParams.toString());
  const activeNav = target?.nav ?? "Ruta no válida";
  const [isAccountMenuOpen, setIsAccountMenuOpen] = useState(false);
  const details = roleDetails[user.role];
  const roleLabel = roleLabels[user.role];

  const screen = resolveScreen(activeNav, { user, startCreate: target?.startCreate ?? false });
  return (
    <main className="role-home">
      <header className="top-nav">
        <Brand compact />
        <nav aria-label="Navegación principal">
          {details.nav.filter((item) => canAccess(user, item)).map((item) => (
            <button
              className={activeNav === item ? "selected" : ""}
              key={item}
              onClick={() => {
                const href = navigationHref(item);
                if (href) router.push(href);
              }}
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
              <button className="logout-option" onClick={onLogout}>
                <img src="/sign-out.svg" alt="" />
                Cerrar sesión
              </button>
            </div>
          )}
        </div>
      </header>
      <section className="home-content">
        {screen.status === "ready" && screen.content !== null ? screen.content : (
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
              <span className="message-mark">{screen.status === "denied" ? "!" : "✓"}</span>
              <div>
                <p className="eyebrow">{activeNav.toUpperCase()}</p>
                <h2>{screen.status === "denied" ? "Acceso denegado" : `Has ingresado como ${roleLabel.toLowerCase()}.`}</h2>
                <p>
                  {screen.status === "denied"
                    ? "No tienes permiso para acceder a esta opción."
                    : screen.status === "ready"
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

export default function AsisApp(props: {
  initialNav?: string;
  startCreate?: boolean;
}) {
  // Legacy page props remain accepted; the current URL is the navigation source.
  void props;
  const router = useRouter();
  const { session, isRestoringSession, sessionValidationError, retrySession, login, logout } = useAuth();
  const exit = async () => {
    await logout();
    router.replace("/login");
  };
  if (isRestoringSession)
    return (
      <main className="app-loading" aria-live="polite">
        Cargando sesión…
      </main>
    );
  if (sessionValidationError)
    return (
      <main className="app-loading">
        <article className="panel">
          <h1>No se pudo comprobar la sesión</h1>
          <p role="alert">{sessionValidationError}</p>
          <button className="primary-action" onClick={() => void retrySession()}>Reintentar</button>
          <button onClick={exit}>Cerrar sesión</button>
        </article>
      </main>
    );
  if (!session) return <LoginScreen onLogin={login} />;
  if (session.user.requiresPasswordChange)
    return <ChangePassword onLogout={exit} />;
  return (
    <Suspense fallback={<main className="app-loading" aria-live="polite">Cargando sesión…</main>}>
      <Dashboard user={session.user} onLogout={exit} />
    </Suspense>
  );
}
