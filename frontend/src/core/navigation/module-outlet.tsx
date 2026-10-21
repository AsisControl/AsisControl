"use client";
import type { ReactNode } from "react";
import type { AuthenticatedUser, UserRole } from "@/core/auth/types";
import { Administration } from "@/modules/administration";
import { Enrollments, GuardianEnrollment } from "@/modules/enrollment";
type ScreenContext = { user: AuthenticatedUser; startCreate: boolean };
type Screen = (context: ScreenContext) => ReactNode;
/**
 * Pantalla de cada opción del menú, por rol. Una opción sin pantalla muestra
 * el aviso de módulo pendiente. El backend sigue siendo quien autoriza.
 */
const screens: Partial<Record<UserRole, Record<string, Screen>>> = {
  ADMINISTRADOR: {
    Matrícula: () => <Enrollments />,
    Administración: ({ user, startCreate }) => (
      <Administration currentUserId={user.id} startCreate={startCreate} />
    ),
  },
  APODERADO: {
    Matrícula: () => <GuardianEnrollment />,
  },
};
export function resolveScreen(
  nav: string,
  context: ScreenContext,
): ReactNode | null {
  const screen = screens[context.user.role]?.[nav];
  return screen ? screen(context) : null;
}
