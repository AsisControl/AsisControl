import {
  clearSession,
  readSession,
  renewSession,
} from "@/core/auth/session-storage";
export const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"
).replace(/\/$/, "");
const DEFAULT_TIMEOUT_MS = 20000;
/** Rutas de sesión que no se reintentan tras renovar el token. */
const SESSION_PATHS = new Set([
  "/api/auth/login",
  "/api/auth/logout",
  "/api/auth/refresh",
]);
export type ApiOptions = RequestInit & {
  /** Tiempo máximo de espera; las cargas de archivos necesitan más. */
  timeoutMs?: number;
};
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
async function request(
  path: string,
  { timeoutMs = DEFAULT_TIMEOUT_MS, ...options }: ApiOptions,
  authenticated: boolean,
  accept: string,
  retried = false,
): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const headers = new Headers(options.headers);
    headers.set("Accept", accept);
    // Con FormData el navegador pone multipart/form-data y su boundary.
    if (options.body && !(options.body instanceof FormData))
      headers.set("Content-Type", "application/json");
    const session = readSession();
    if (session && authenticated)
      headers.set("Authorization", `Bearer ${session.accessToken}`);
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers,
      signal: controller.signal,
      cache: "no-store",
    });
    if (
      response.status === 401 &&
      session &&
      authenticated &&
      !retried &&
      !SESSION_PATHS.has(path) &&
      (await renewSession())
    )
      // Otra pestaña pudo renovar la sesión y dejar vencido este token: se repite una vez.
      return request(path, { timeoutMs, ...options }, authenticated, accept, true);
    if (!response.ok) {
      const body = await response.json().catch(() => null);
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
    return response;
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
export async function api<T>(
  path: string,
  options: ApiOptions = {},
  authenticated = true,
): Promise<T> {
  const response = await request(
    path,
    options,
    authenticated,
    "application/json",
  );
  const body =
    response.status === 204 ? null : await response.json().catch(() => null);
  return body as T;
}
/** Descarga un archivo (por ejemplo, una plantilla Excel) con la sesión actual. */
export async function apiBlob(
  path: string,
  options: ApiOptions = {},
): Promise<Blob> {
  const response = await request(path, options, true, "*/*");
  return response.blob();
}
export const message = (error: unknown) =>
  error instanceof Error ? error.message : "No se pudo completar la operación.";
/** Errores por campo que devolvió el backend; vacío si no los hay. */
export function fieldErrorsOf(error: unknown): Record<string, string> {
  const fields: Record<string, string> = {};
  if (error instanceof ApiError)
    for (const item of error.fieldErrors)
      if (!Object.hasOwn(fields, item.field)) fields[item.field] = item.message;
  return fields;
}
