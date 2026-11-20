import { API_BASE_URL, ApiError, api, message } from "@/core/api/client";
import { clearSession } from "./session-storage";
import type { AuthService, AuthSession, UserRole } from "./types";
export type UserSummary = {
  id: number;
  username: string;
  nombreCompleto: string;
  rol: string | null;
  rolesDisponibles: string[];
  sesionId: number;
  versionContexto: number;
  permisos: string[];
  requiereCambioContrasena: boolean;
};
export type AuthResponse = {
  accessToken: string;
  expiresAt: string;
  user: UserSummary;
};
const roles: ReadonlySet<string> = new Set([
  "ADMINISTRADOR", "DOCENTE", "ESTUDIANTE", "APODERADO",
]);
function fromBackendRole(role: string): UserRole | null {
  const name = role === "ALUMNO" ? "ESTUDIANTE" : role;
  return roles.has(name) ? name as UserRole : null;
}

export function mapAuth(r: AuthResponse): AuthSession {
  const role = r.user.rol === null ? null : fromBackendRole(r.user.rol);
  if (r.user.rol !== null && !role)
    throw new Error(
      "El rol de este acceso no está habilitado en esta interfaz.",
    );
  const availableRoles = (r.user.rolesDisponibles ?? (role ? [role] : []))
    .map(fromBackendRole)
    .filter((candidate): candidate is UserRole => candidate !== null);
  return {
    accessToken: r.accessToken,
    expiresAt: r.expiresAt,
    user: {
      id: String(r.user.id),
      username: r.user.username,
      fullName: r.user.nombreCompleto,
      role,
      availableRoles: [...new Set(availableRoles)],
      sessionId: String(r.user.sesionId ?? r.user.id),
      contextVersion: r.user.versionContexto ?? 0,
      permissions: role ? r.user.permisos : [],
      requiresPasswordChange: r.user.requiereCambioContrasena,
    },
  };
}
const REFRESH_TIMEOUT_MS = 20000;
/** Login, logout y refresh envían y reciben la cookie HttpOnly de renovación. */
export const httpAuthService: AuthService = {
  async login(credentials) {
    try {
      return {
        ok: true,
        session: mapAuth(
          await api<AuthResponse>("/api/auth/login", {
            method: "POST",
            credentials: "include",
            body: JSON.stringify({
              identifier: credentials.username.trim(),
              password: credentials.password,
            }),
          }, false),
        ),
      };
    } catch (e) {
      return { ok: false, message: message(e) };
    }
  },
  async switchRole(role) {
    return mapAuth(
      await api<AuthResponse>("/api/auth/rol-activo", {
        method: "PUT",
        credentials: "omit",
        body: JSON.stringify({ rol: role === "ESTUDIANTE" ? "ALUMNO" : role }),
      }),
    );
  },
  async logout() {
    const options = { method: "POST", credentials: "include" } as const;
    try {
      await api("/api/auth/logout", options);
    } catch (error) {
      // Si el rol cambió en otra pestaña, aún se puede revocar la cookie.
      if (!(error instanceof ApiError) || error.status !== 401) throw error;
      await api("/api/auth/logout", options, false);
    } finally {
      clearSession();
    }
  },
  /**
   * Fetch directo: un rechazo (sin cookie, vencida o revocada) no debe cerrar
   * la sesión de otras pestañas como lo hace el 401 de api().
   */
  async refresh() {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), REFRESH_TIMEOUT_MS);
    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
        method: "POST",
        headers: { Accept: "application/json" },
        credentials: "include",
        cache: "no-store",
        signal: controller.signal,
      });
      const body = await response.json().catch(() => null);
      if (response.status >= 500)
        throw new Error(body?.message || "No se pudo renovar la sesión.");
      return response.ok ? mapAuth(body as AuthResponse) : null;
    } finally {
      clearTimeout(timer);
    }
  },
};
