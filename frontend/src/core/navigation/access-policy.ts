import type { AuthenticatedUser } from "@/core/auth/types";
import { roleDetails } from "./navigation";

/** Presentation checks only. The backend authorizes every API operation. */
export function canAccess(
  user: AuthenticatedUser,
  nav: string,
  startCreate = false,
): boolean {
  if (!roleDetails[user.role].nav.includes(nav)) return false;
  const permissions = user.permissions ?? [];
  if (nav === "Matrícula") return permissions.includes("MATRICULA_LEER");
  if (nav === "Administración")
    return (
      user.role === "ADMINISTRADOR" &&
      permissions.includes("USUARIOS_LEER") &&
      (!startCreate || permissions.includes("USUARIOS_ESCRIBIR"))
    );
  // Pending modules retain their existing role-based availability.
  return true;
}
