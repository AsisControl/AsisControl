"use client";

/* eslint-disable @next/next/no-img-element -- SVG assets are served from public/. */

import { useEffect, useRef, useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { sessionContextKey } from "@/core/auth/session-storage";
import { ChangePassword } from "@/core/auth/change-password";
import { useAuth } from "@/core/auth/use-auth";
import type { AuthenticatedUser, SessionUser, UserRole } from "@/core/auth/types";
import { canAccess } from "@/core/navigation/access-policy";
import {
  CHANGE_PASSWORD_PATH,
  navFromSegment,
  navPath,
  roleDetails,
  roleLabels,
} from "@/core/navigation/navigation";
import { NavIcon } from "@/core/navigation/nav-icons";
import { Loading } from "@/shared/ui";
import { AsisControlLogo } from "./brand";
import { LoginScreen } from "./login-screen";
import { SessionUserProvider } from "./session-user";
import { RoleContext } from "./role-context";
import { RoleChooser } from "./role-chooser";
import "./role-theme.css";
import styles from "./app-shell.module.css";

const MOBILE_BREAKPOINT_PX = 760;

function Dashboard({
  user,
  onLogout,
  onSwitchRole,
  validationError,
  onRetryValidation,
  children,
}: {
  user: AuthenticatedUser & SessionUser;
  onLogout: () => Promise<void>;
  onSwitchRole: (role: UserRole) => Promise<void>;
  validationError: string;
  onRetryValidation: () => Promise<void>;
  children: ReactNode;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const [isAccountMenuOpen, setIsAccountMenuOpen] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [choosingRole, setChoosingRole] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);
  const header = useRef<HTMLElement>(null);
  const details = roleDetails[user.role];
  const roleLabel = roleLabels[user.role];
  const activeNav = navFromSegment(user.role, pathname.split("/")[1]);
  const visibleNav = details.nav.filter((item) => canAccess(user, item));

  useEffect(() => {
    if (!isMobileMenuOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      setIsMobileMenuOpen(false);
      menuButton.current?.focus();
    };
    const onPointerDown = (event: PointerEvent) => {
      if (
        event.target instanceof Node &&
        !header.current?.contains(event.target)
      )
        setIsMobileMenuOpen(false);
    };
    const onResize = () => {
      if (window.innerWidth > MOBILE_BREAKPOINT_PX) setIsMobileMenuOpen(false);
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

  // El alto del menú cambia con el ancho de pantalla: otros elementos fijos se colocan debajo.
  useEffect(() => {
    const element = header.current;
    if (!element || typeof ResizeObserver === "undefined") return;
    const root = document.documentElement;
    const observer = new ResizeObserver(() =>
      root.style.setProperty("--header-height", `${element.offsetHeight}px`),
    );
    observer.observe(element);
    return () => {
      observer.disconnect();
      root.style.removeProperty("--header-height");
    };
  }, [pathname]);

  if (pathname === CHANGE_PASSWORD_PATH)
    return <ChangePassword onLogout={onLogout} mandatory={false} />;
  function go(path: string) {
    setIsAccountMenuOpen(false);
    setIsMobileMenuOpen(false);
    router.push(path);
  }
  return (
    <main className="role-home" data-role={user.role}>
      <header ref={header} className={`top-nav ${styles.header}`}>
        <button
          ref={menuButton}
          type="button"
          className={styles.menuButton}
          aria-label={
            isMobileMenuOpen
              ? "Cerrar menú de navegación"
              : "Abrir menú de navegación"
          }
          aria-expanded={isMobileMenuOpen}
          aria-controls="main-navigation"
          onClick={() => {
            setIsMobileMenuOpen(!isMobileMenuOpen);
            setIsAccountMenuOpen(false);
          }}
        >
          <svg
            width="24"
            height="24"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            aria-hidden="true"
          >
            {isMobileMenuOpen ? (
              <path d="m6 6 12 12M6 18 18 6" />
            ) : (
              <path d="M4 6h16M4 12h16M4 18h16" />
            )}
          </svg>
        </button>
        <div className={styles.dashboardBrand}>
          <AsisControlLogo inverse />
          <RoleContext role={user.role} />
        </div>
        <nav
          id="main-navigation"
          className={`${styles.navBar} ${isMobileMenuOpen ? styles.open : ""}`}
          aria-label="Navegación principal"
        >
          {visibleNav.map((item) => (
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
            onClick={() => {
              setIsAccountMenuOpen(!isAccountMenuOpen);
              setIsMobileMenuOpen(false);
            }}
          >
            <span className={styles.accountIdentity}>
              <span className={styles.roleBadge}>{user.role}</span>
              <strong>{user.fullName}</strong>
            </span>
            <span className={`avatar ${styles.accountAvatar}`}>
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
              {user.availableRoles?.length > 1 && (
                <button
                  type="button"
                  className="logout-option"
                  onClick={() => {
                    setIsAccountMenuOpen(false);
                    setChoosingRole(true);
                  }}
                >
                  Cambiar de rol
                </button>
              )}
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
      {validationError && (
        <div className={styles.sessionAlert} role="alert">
          <span>
            <strong>No se pudo comprobar la sesión.</strong> {validationError}
          </span>
          <button type="button" onClick={() => void onRetryValidation()}>
            Reintentar
          </button>
        </div>
      )}
      {choosingRole && (
        <RoleChooser
          user={user}
          onSelect={async (role) => {
            await onSwitchRole(role);
            setChoosingRole(false);
          }}
          onClose={() => setChoosingRole(false)}
        />
      )}
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
  const pathname = usePathname();
  const {
    session,
    isRestoringSession,
    isEndingSession,
    contextSuspended,
    sessionValidationError,
    retrySession,
    login,
    logout,
    switchRole,
  } = useAuth();
  const context = sessionContextKey(session);
  const [route, setRoute] = useState({ context, returning: false });
  if (route.context !== context)
    setRoute({
      context,
      returning: Boolean(route.context && context && pathname !== "/"),
    });
  else if (route.returning && pathname === "/")
    setRoute({ context, returning: false });
  useEffect(() => {
    if (route.returning && pathname !== "/") router.replace("/");
  }, [pathname, route.returning, router]);
  async function logoutToHome() {
    await logout();
    router.replace("/");
  }
  if (contextSuspended && sessionValidationError)
    return (
      <main className="app-loading">
        <div className={styles.sessionAlert} role="alert">
          <p>{sessionValidationError}</p>
          <button type="button" onClick={() => void retrySession()}>Reintentar</button>
          <button type="button" onClick={() => void logoutToHome()}>Cerrar sesión</button>
        </div>
      </main>
    );
  if (isRestoringSession || isEndingSession || contextSuspended || route.returning)
    return (
      <main className="app-loading">
        <Loading variant="screen">
          {isEndingSession ? "Cerrando sesión…" : "Cargando sesión…"}
        </Loading>
      </main>
    );
  if (!session) return <LoginScreen onLogin={login} />;
  if (session.user.requiresPasswordChange)
    return <ChangePassword onLogout={logoutToHome} />;
  if (!session.user.role)
    return (
      <RoleChooser
        user={session.user}
        onSelect={switchRole}
        onClose={() => void logoutToHome()}
      />
    );
  const user = { ...session.user, role: session.user.role };
  return (
    <SessionUserProvider key={context} value={user}>
      <Dashboard
        user={user}
        onLogout={logoutToHome}
        onSwitchRole={switchRole}
        validationError={sessionValidationError}
        onRetryValidation={retrySession}
      >
        {children}
      </Dashboard>
    </SessionUserProvider>
  );
}
