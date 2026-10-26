import { API_BASE_URL, api, message } from "@/core/api/client";
import { clearSession } from "./session-storage";
import type { AuthService, AuthSession, UserRole } from "./types";
export type UserSummary = {
  id: number;
  username: string;
  nombreCompleto: string;
  rol: string;
  permisos: string[];
  requiereCambioContrasena: boolean;
};
export type AuthResponse = {
  accessToken: string;
  expiresAt: string;
  user: UserSummary;
};
export function mapAuth(r: AuthResponse): AuthSession {
  const role = r.user.rol === "ALUMNO" ? "ESTUDIANTE" : r.user.rol;
  if (!["ADMINISTRADOR", "DOCENTE", "ESTUDIANTE", "APODERADO"].includes(role))
    throw new Error(
      "El rol de este acceso no está habilitado en esta interfaz.",
    );
  return {
    accessToken: r.accessToken,
    expiresAt: r.expiresAt,
    user: {
      id: String(r.user.id),
      username: r.user.username,
      fullName: r.user.nombreCompleto,
      role: role as UserRole,
      permissions: r.user.permisos,
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
          }),
        ),
      };
    } catch (e) {
      return { ok: false, message: message(e) };
    }
  },
  async logout() {
    try {
      await api("/api/auth/logout", {
        method: "POST",
        credentials: "include",
      });
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
