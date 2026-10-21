import { api, message } from "@/core/api/client";
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
export const httpAuthService: AuthService = {
  async login(credentials) {
    try {
      return {
        ok: true,
        session: mapAuth(
          await api<AuthResponse>("/api/auth/login", {
            method: "POST",
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
      await api("/api/auth/logout", { method: "POST" });
    } finally {
      clearSession();
    }
  },
};
