"use client";
import type { ReactNode } from "react";
import type { AuthenticatedUser, UserRole } from "@/core/auth/types";
import { Administration } from "@/modules/administration";
import { Enrollments, GuardianEnrollment } from "@/modules/enrollment";
import { canAccess } from "./access-policy";
type ScreenContext = { user: AuthenticatedUser; startCreate: boolean };
type Screen = (context: ScreenContext) => ReactNode;
export type ScreenResult =
  | { status: "ready"; content: ReactNode }
  | { status: "pending" }
  | { status: "denied" };
/**
 * Pantalla de cada opción del menú, por rol. Una opción sin pantalla muestra
 * el aviso de módulo pendiente. El backend sigue siendo quien autoriza.
 */
const screens: Partial<Record<UserRole, Record<string, Screen>>> = {
  ADMINISTRADOR: {
    Matrícula: () => <Enrollments />,
    Administración: ({ user, startCreate }) => (
      <Administration key={startCreate ? "create-access" : "administration"} currentUserId={user.id} startCreate={startCreate} />
    ),
  },
  APODERADO: {
    Matrícula: () => <GuardianEnrollment />,
  },
};
export function resolveScreen(
  nav: string,
  context: ScreenContext,
): ScreenResult {
  if (!canAccess(context.user, nav, context.startCreate))
    return { status: "denied" };
  if (nav === "Inicio") return { status: "ready", content: null };
  const screen = screens[context.user.role]?.[nav];
  return screen
    ? { status: "ready", content: screen(context) }
    : { status: "pending" };
}
