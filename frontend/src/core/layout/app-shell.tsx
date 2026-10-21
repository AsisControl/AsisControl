"use client";

/* eslint-disable @next/next/no-img-element -- SVG assets are served from public/. */

import { Suspense, useEffect, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { ChangePassword } from "@/core/auth/change-password";
import { useAuth } from "@/core/auth/use-auth";
import type { AuthenticatedUser } from "@/core/auth/types";
import { roleDetails, roleLabels } from "@/core/navigation/navigation";
import { resolveScreen } from "@/core/navigation/module-outlet";
import { canAccess } from "@/core/navigation/access-policy";
import { navigationHref, resolveNavigation } from "@/core/navigation/navigation-routes";
import { clearPendingDestination, consumeDestination, rememberDestination } from "@/core/navigation/pending-destination";
import { Brand } from "./brand";
import { LoginScreen } from "./login-screen";
import styles from "./app-shell.module.css";

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
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);
  const header = useRef<HTMLElement>(null);
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && isMobileMenuOpen) {
        setIsMobileMenuOpen(false);
        menuButton.current?.focus();
      }
    };
    const onPointerDown = (event: PointerEvent) => {
      if (event.target instanceof Node && !header.current?.contains(event.target))
        setIsMobileMenuOpen(false);
    };
    const onResize = () => {
      if (window.innerWidth > 760) setIsMobileMenuOpen(false);
    };
    document.addEventListener("keydown", onKeyDown);
    document.addEventListener("pointerdown", onPointerDown);
    window.addEventListener("resize", onResize);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.removeEventListener("pointerdown", onPointerDown);
      window.removeEventListener("resize", onResize);
    };
  }, [isMobileMenuOpen]);
  const details = roleDetails[user.role];
  const roleLabel = roleLabels[user.role];

  const screen = resolveScreen(activeNav, { user, startCreate: target?.startCreate ?? false });
  return (
    <main className="role-home">
      <header ref={header} className={`top-nav ${styles.header}`}>
        <Brand compact />
        <button
          ref={menuButton}
          type="button"
          className={styles.menuButton}
          aria-label={isMobileMenuOpen ? "Cerrar menú de navegación" : "Abrir menú de navegación"}
          aria-expanded={isMobileMenuOpen}
          aria-controls="main-navigation"
          onClick={() => {
            setIsMobileMenuOpen((open) => !open);
            setIsAccountMenuOpen(false);
          }}
        >
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            {isMobileMenuOpen ? <path d="m6 6 12 12M6 18 18 6" /> : <path d="M4 6h16M4 12h16M4 18h16" />}
          </svg>
        </button>
        <nav id="main-navigation" className={`${styles.navigation} ${isMobileMenuOpen ? styles.open : ""}`} aria-label="Navegación principal">
          {details.nav.filter((item) => canAccess(user, item)).map((item) => (
            <button
              className={activeNav === item ? "selected" : ""}
              aria-current={activeNav === item ? "page" : undefined}
              key={item}
              onClick={() => {
                const href = navigationHref(item);
                if (href) {
                  setIsMobileMenuOpen(false);
                  menuButton.current?.focus();
                  router.push(href);
                }
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
            onClick={() => {
              setIsAccountMenuOpen(!isAccountMenuOpen);
              setIsMobileMenuOpen(false);
            }}
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

function SessionApp() {
  const router = useRouter();
  const pathname = usePathname();
  const search = useSearchParams().toString();
  const leaving = useRef(false);
  const returnedSession = useRef<string | null>(null);
  const passwordContainer = useRef<HTMLDivElement>(null);
  const { session, sessionKey, isRestoringSession, isRevalidatingSession, isEndingSession, sessionValidationError, retrySession, login, logout } = useAuth();
  const endSession = async (preserveDestination: boolean) => {
    leaving.current = true;
    if (!preserveDestination) clearPendingDestination();
    await logout();
    router.replace("/login");
  };
  const exit = () => endSession(false);
  const exitFromPassword = () => {
    // The unchanged component shares one callback for cancel and completion.
    // Inspect structure only, never input values: completion has no form.
    return endSession(!!passwordContainer.current && !passwordContainer.current.querySelector("form"));
  };
  const checking = isRestoringSession || isRevalidatingSession;
  const blocked = checking || !!sessionValidationError;
  useEffect(() => {
    if (pathname === "/login") leaving.current = false;
    else returnedSession.current = null;
    if (blocked || isEndingSession || leaving.current) return;
    if (!session && pathname !== "/login") {
      rememberDestination(`${pathname}${search ? `?${search}` : ""}`);
      router.replace("/login");
    } else if (session && !session.user.requiresPasswordChange && pathname === "/login" && returnedSession.current !== sessionKey) {
      returnedSession.current = sessionKey;
      router.replace(consumeDestination());
    }
  }, [pathname, search, blocked, isEndingSession, session, sessionKey, router]);
  const validationNotice = checking ? (
      <main className="app-loading" aria-live="polite">
        Cargando sesión…
      </main>
    ) : sessionValidationError ? (
      <main className="app-loading">
        <article className="panel">
          <h1>No se pudo comprobar la sesión</h1>
          <p role="alert">{sessionValidationError}</p>
          <button className="primary-action" onClick={() => void retrySession()}>Reintentar</button>
          <button onClick={exit}>Cerrar sesión</button>
        </article>
      </main>
    ) : null;
  if (session?.user.requiresPasswordChange && !isEndingSession)
    return (
      <>
        <div
          ref={passwordContainer}
          key={sessionKey}
          hidden={blocked}
          inert={blocked}
          aria-busy={blocked}
          onSubmitCapture={(event) => {
            if (blocked) {
              event.preventDefault();
              event.stopPropagation();
            }
          }}
        >
          <ChangePassword onLogout={exitFromPassword} />
        </div>
        {validationNotice}
      </>
    );
  if (blocked) return validationNotice;
  if (!session && pathname === "/login") return <LoginScreen onLogin={login} />;
  if (!session || pathname === "/login")
    return <main className="app-loading" aria-live="polite">Cargando sesión…</main>;
  return (
    <Suspense fallback={<main className="app-loading" aria-live="polite">Cargando sesión…</main>}>
      <Dashboard user={session.user} onLogout={exit} />
    </Suspense>
  );
}

export default function AsisApp(props: { initialNav?: string; startCreate?: boolean }) {
  // Legacy page props remain accepted; the URL is the navigation source.
  void props;
  return (
    <Suspense fallback={<main className="app-loading" aria-live="polite">Cargando sesión…</main>}>
      <SessionApp />
    </Suspense>
  );
}
