import { api } from "@/core/api/client";
import type { Account, Role } from "../../types";
/** Ruta base del recurso; el listado paginado se consume con usePaged. */
export const USUARIOS_PATH = "/api/usuarios";
export type AccessUpdate = {
  correo: string;
  estado: string;
  correoVerificado: boolean;
  temporaryPassword: string | null;
  motivo: string;
};
export type PasswordReset = {
  temporaryPassword: string;
  identidadVerificada: boolean;
  motivo: string;
};
export function obtenerUsuario(id: number) {
  return api<Account>(`${USUARIOS_PATH}/${id}`);
}
export function listarRoles() {
  return api<Role[]>("/api/roles");
}
/** El rol no cambia desde la edición: para otro rol se crea un acceso separado. */
export function actualizarUsuario(account: Account, update: AccessUpdate) {
  return api<Account>(`${USUARIOS_PATH}/${account.id}`, {
    method: "PUT",
    body: JSON.stringify({ ...update, rolId: account.rolId }),
  });
}
export function restablecerContrasena(id: number, reset: PasswordReset) {
  return api<null>(`${USUARIOS_PATH}/${id}/contrasena-temporal`, {
    method: "PUT",
    body: JSON.stringify(reset),
  });
}
