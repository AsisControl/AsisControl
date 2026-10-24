import { api } from "@/core/api/client";
/** Ruta base del registro; el listado paginado se consume con usePaged. */
export const AUDITORIA_REGISTROS_PATH = "/api/auditoria/registros";
/** Módulos que ya tienen registros, para el filtro. */
export function listarModulos() {
  return api<string[]>("/api/auditoria/modulos");
}
/** Acciones que ya tienen registros, para el filtro. */
export function listarAcciones() {
  return api<string[]>("/api/auditoria/acciones");
}
