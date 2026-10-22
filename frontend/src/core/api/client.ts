import { clearSession, readSession } from "@/core/auth/session-storage";
const base = (
  process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"
).replace(/\/$/, "");
export type ApiFieldError = { field: string; message: string };

type RawApiFieldError = {
  field?: unknown;
  message?: unknown;
  campo?: unknown;
  mensaje?: unknown;
};

function parseFieldErrors(values: unknown): ApiFieldError[] {
  if (!Array.isArray(values)) return [];
  return values.flatMap((item: RawApiFieldError | null) => {
    const field = item?.field ?? item?.campo;
    const errorMessage = item?.message ?? item?.mensaje;
    if (typeof field !== "string" || typeof errorMessage !== "string") {
      return [];
    }
    return [{ field, message: errorMessage }];
  });
}
/** Conserva los errores de negocio sin añadir códigos técnicos al texto visible. */
export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code: string = "",
    public readonly fieldErrors: ApiFieldError[] = [],
    public readonly details: unknown = null,
  ) {
    super(message);
    this.name = "ApiError";
  }
}
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
    const response = await fetch(`${base}${path}`, {
      ...options,
      headers,
      signal: controller.signal,
      cache: "no-store",
    });
    const body =
      response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok) {
      if (response.status === 401 && path !== "/api/auth/login") clearSession();
      throw new ApiError(
        body?.message ||
          (response.status === 403
            ? "No tienes permiso para realizar esta operación."
            : "No se pudo completar la operación."),
        response.status,
        typeof body?.code === "string" ? body.code : "",
        parseFieldErrors(body?.fieldErrors),
        body?.details ?? null,
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
