"use client";
import { useEffect, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import type { AuthenticatedUser, UserRole } from "@/core/auth/types";
import { useSessionUser } from "@/core/layout/session-user";
import { Administration } from "@/modules/administration";
import { Enrollments, GuardianEnrollment } from "@/modules/enrollment";
import {
  HOME_NAV,
  navFromSegment,
  roleDetails,
  roleLabels,
} from "./navigation";
/** Segmentos de la URL después de la opción del menú: /administracion/usuarios → ["usuarios"]. */
type ScreenContext = { user: AuthenticatedUser; subpath: string[] };
type Screen = (context: ScreenContext) => ReactNode;
/**
 * Pantalla de cada opción del menú, por rol. Una opción sin pantalla muestra
 * el aviso de módulo pendiente. El backend sigue siendo quien autoriza.
 */
const screens: Partial<Record<UserRole, Record<string, Screen>>> = {
  ADMINISTRADOR: {
    Matrícula: () => <Enrollments />,
    Administración: ({ user, subpath: [tab, view] }) => (
      <Administration currentUserId={user.id} tab={tab} view={view} />
    ),
  },
  APODERADO: {
    Matrícula: () => <GuardianEnrollment />,
  },
};
/** Direcciones anteriores que se conservan para no romper enlaces guardados. */
const legacyPaths: Record<string, string> = {
  "/login": "/",
  "/estudiantes": "/matricula",
  "/administracion/accesos/nuevo": "/administracion/usuarios/nuevo",
};
export function ModuleOutlet() {
  const user = useSessionUser();
  const router = useRouter();
  const pathname = usePathname();
  const redirect = legacyPaths[pathname.replace(/\/$/, "")];
  useEffect(() => {
    if (redirect) router.replace(redirect);
  }, [redirect, router]);
  if (redirect) return null;
  const [segment, ...subpath] = pathname.split("/").filter(Boolean);
  const nav = navFromSegment(user.role, segment);
  if (!nav) return <NotFound />;
  const screen = screens[user.role]?.[nav];
  return screen ? screen({ user, subpath }) : <PendingModule nav={nav} />;
}
function PendingModule({ nav }: { nav: string }) {
  const user = useSessionUser();
  const roleLabel = roleLabels[user.role];
  return (
    <>
      <div className="page-title">
        <div>
          <h1>
            {nav === HOME_NAV
              ? ["DOCENTE", "ESTUDIANTE"].includes(user.role)
                ? `Bienvenido/a, ${user.fullName}`
                : roleDetails[user.role].title
              : nav}
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
          <p className="eyebrow">{nav.toUpperCase()}</p>
          <h2>Has ingresado como {roleLabel.toLowerCase()}.</h2>
          <p>
            {nav === HOME_NAV
              ? "Selecciona una opción del menú para continuar."
              : "Este módulo todavía está pendiente de implementación."}
          </p>
        </div>
      </article>
    </>
  );
}
function NotFound() {
  const router = useRouter();
  return (
    <article className="role-message">
      <span className="message-mark">!</span>
      <div>
        <p className="eyebrow">PÁGINA NO ENCONTRADA</p>
        <h2>Esta dirección no existe o no está disponible para tu rol.</h2>
        <button className="primary-action" onClick={() => router.push("/")}>
          Ir al inicio
        </button>
      </div>
    </article>
  );
}
