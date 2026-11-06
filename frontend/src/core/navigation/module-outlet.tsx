"use client";
import { useEffect, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import type { AuthenticatedUser, UserRole } from "@/core/auth/types";
import { useSessionUser } from "@/core/layout/session-user";
import { Academic } from "@/modules/academic";
import { Administration } from "@/modules/administration";
import { JustificationInbox } from "@/modules/attendance";
import { Enrollments, GuardianEnrollment } from "@/modules/enrollment";
import { Finance } from "@/modules/finance";
import { GuardianAttendance } from "@/modules/guardian-attendance";
import { Schedules, TeacherSchedule } from "@/modules/schedules";
import { TasksModule } from "@/modules/tasks";
import { TeacherCoursesModule } from "@/modules/teacher-courses";
import { TeacherHome } from "@/modules/teacher-home";
import { canAccess } from "./access-policy";
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
  ESTUDIANTE: {
    Horario: () => <StudentSchedule />,
  },
  DOCENTE: {
    Inicio: ({ user }) => <TeacherHome fullName={user.fullName} />,
    "Mis cursos": ({ subpath }) => <TeacherCoursesModule subpath={subpath} />,
    Asistencia: () => <JustificationInbox />,
    Horario: () => <TeacherSchedule />,
    Evaluaciones: ({ user, subpath: [tab, evaluationId] }) => (
      <Evaluations
        tab={tab}
        evaluationId={evaluationId}
        canWrite={(user.permissions ?? []).includes("CALIFICACIONES_ESCRIBIR")}
      />
    ),
    Tareas: ({ user, subpath }) => (
      <TasksModule
        subpath={subpath}
        canWrite={(user.permissions ?? []).includes("TAREAS_ESCRIBIR")}
        canUpload={(user.permissions ?? []).includes("ARCHIVOS_ESCRIBIR")}
      />
    ),
  },
  ADMINISTRADOR: {
    Matrícula: ({ subpath: [tab, view] }) => <Enrollments tab={tab} view={view} />,
    Finanzas: ({ user }) => (
      <Finance canWrite={(user.permissions ?? []).includes("PAGOS_ESCRIBIR")} />
    ),
    Académico: ({ user, subpath: [tab] }) => (
      <Academic
        tab={tab}
        canWrite={(user.permissions ?? []).includes("ACADEMICO_ESCRIBIR")}
      />
    ),
    Horarios: ({ user, subpath: [tab] }) => (
      <Schedules
        tab={tab}
        canWrite={(user.permissions ?? []).includes("ACADEMICO_ESCRIBIR")}
        canViewHistory={(user.permissions ?? []).includes("AUDITORIA_LEER")}
      />
    ),
    Administración: ({ user, subpath: [tab, view] }) => (
      <Administration currentUserId={user.id} tab={tab} view={view} />
    ),
  },
  APODERADO: {
    Matrícula: () => <GuardianEnrollment />,
    Asistencia: () => <GuardianAttendance />,
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
  if (!canAccess(user, nav, subpath)) return <AccessDenied />;
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
  return (
    <BlockedRoute
      eyebrow="PÁGINA NO ENCONTRADA"
      title="Esta dirección no existe o no está disponible para tu rol."
    />
  );
}
function AccessDenied() {
  return (
    <BlockedRoute
      eyebrow="ACCESO DENEGADO"
      title="No tienes permiso para acceder a esta opción."
    />
  );
}
function BlockedRoute({ eyebrow, title }: { eyebrow: string; title: string }) {
  const router = useRouter();
  return (
    <article className="role-message">
      <span className="message-mark">!</span>
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h2>{title}</h2>
        <button className="primary-action" onClick={() => router.push("/")}>
          Ir al inicio
        </button>
      </div>
    </article>
  );
}
