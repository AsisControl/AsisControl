import type { AuthenticatedUser } from "@/core/auth/types";
import { roleDetails } from "./navigation";
/** Permiso que exige cada opción con pantalla; las pendientes dependen solo del rol. */
const requiredPermissions: Record<string, string> = {
  Matrícula: "MATRICULA_LEER",
  Académico: "ACADEMICO_LEER",
  Administración: "USUARIOS_LEER",
};
const USERS_WRITE_PERMISSION = "USUARIOS_ESCRIBIR";
/**
 * Comprobación solo de presentación: oculta lo que el usuario no puede usar.
 * El backend sigue autorizando cada operación.
 */
export function canAccess(
  user: AuthenticatedUser,
  nav: string,
  subpath: string[] = [],
): boolean {
  if (!roleDetails[user.role].nav.includes(nav)) return false;
  const permissions = user.permissions ?? [];
  const required = requiredPermissions[nav];
  if (required && !permissions.includes(required)) return false;
  const [tab, view] = subpath;
  if (nav === "Administración" && tab === "usuarios" && view === "nuevo")
    return permissions.includes(USERS_WRITE_PERMISSION);
  return true;
}
