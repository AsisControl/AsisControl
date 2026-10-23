import { clearSession, readSession } from "@/core/auth/session-storage";
export const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"
).replace(/\/$/, "");
export async function api<T>(
  path: string,
  options: RequestInit = {},
  authenticated = true,
): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 20000);
  try {
    const headers = new Headers(options.headers);
    headers.set("Accept", "application/json");
    if (options.body) headers.set("Content-Type", "application/json");
    const session = readSession();
    if (session && authenticated)
      headers.set("Authorization", `Bearer ${session.accessToken}`);
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers,
      signal: controller.signal,
      cache: "no-store",
    });
    const body =
      response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok) {
      if (response.status === 401 && path !== "/api/auth/login") clearSession();
      throw new Error(
        body?.message ||
          (response.status === 403
            ? "No tienes permiso para realizar esta operación."
            : "No se pudo completar la operación."),
      );
    }
    return body as T;
  } catch (error) {
    if (error instanceof TypeError)
      throw new Error(
        "No se pudo conectar con el servidor. Comprueba que el backend esté iniciado.",
      );
    if (error instanceof Error && error.name === "AbortError")
      throw new Error("El servidor tardó demasiado. Inténtalo nuevamente.");
    throw error;
  } finally {
    clearTimeout(timer);
  }
}
export const message = (error: unknown) =>
  error instanceof Error ? error.message : "No se pudo completar la operación.";
