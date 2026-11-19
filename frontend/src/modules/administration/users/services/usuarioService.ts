import { api } from "@/core/api/client";
import type { personData } from "@/shared/forms/person-fields";
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
export function actualizarUsuario(account: Account, update: AccessUpdate) {
  return api<Account>(`${USUARIOS_PATH}/${account.id}`, {
    method: "PUT",
    body: JSON.stringify(update),
  });
}
export function actualizarRolUsuario(id: number, rolId: number, activo: boolean, motivo: string) {
  return api<Account>(`${USUARIOS_PATH}/${id}/roles/${rolId}`, {
    method: "PUT",
    body: JSON.stringify({ activo, motivo }),
  });
}
export function registrarAdministrador(
  input: { personaId: number } | { persona: ReturnType<typeof personData> },
) {
  return api<Pick<Account, "id" | "username" | "correo" | "nombreCompleto" | "contrasenaTemporalExpira"> & {
    contrasenaTemporal: string | null;
  }>(`${USUARIOS_PATH}/administradores`, {
    method: "POST",
    body: JSON.stringify(input),
  });
}
export function restablecerContrasena(id: number, reset: PasswordReset) {
  return api<null>(`${USUARIOS_PATH}/${id}/contrasena-temporal`, {
    method: "PUT",
    body: JSON.stringify(reset),
  });
}
