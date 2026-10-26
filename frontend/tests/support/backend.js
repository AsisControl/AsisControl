import { vi } from "vitest";
import { clearSession, saveSession } from "@/core/auth/session-storage";

/** Respuesta con un código distinto de 200: `reply(409, { message: "..." })`. */
export const reply = (status, body = null) => ({ status, body, isReply: true });

/** Página de Spring con los campos que normaliza shared/lib/pagination. */
export const page = (content, totalPages = 1) => ({
  content,
  page: 0,
  totalElements: content.length,
  totalPages,
});

const toResponse = ({ status, body }) => ({
  status,
  ok: status >= 200 && status < 300,
  json: async () => body,
});

/** "/api/usuarios/:id" coincide con "/api/usuarios/42". */
const matches = (pattern, path) => {
  const expected = pattern.split("/");
  const actual = path.split("/");
  return (
    expected.length === actual.length &&
    expected.every((part, index) => part.startsWith(":") || part === actual[index])
  );
};

/**
 * Reemplaza fetch por un backend en memoria. Cada ruta es "MÉTODO /ruta" y su
 * valor es el cuerpo de la respuesta (200) o una función que recibe la llamada.
 * Devuelve las llamadas recibidas para comprobar qué envió la pantalla.
 */
export function fakeBackend(routes = {}) {
  const calls = [];
  const fetcher = vi.fn(async (url, options = {}) => {
    const method = options.method ?? "GET";
    const { pathname, searchParams } = new URL(url);
    const call = {
      method,
      path: pathname,
      query: Object.fromEntries(searchParams),
      body: options.body ? JSON.parse(options.body) : undefined,
    };
    calls.push(call);
    const key = Object.keys(routes).find((route) => {
      const [routeMethod, routePath] = route.split(" ");
      return routeMethod === method && matches(routePath, pathname);
    });
    if (!key)
      return toResponse(
        reply(404, { message: `Ruta no simulada: ${method} ${pathname}` }),
      );
    const handler = routes[key];
    const result = typeof handler === "function" ? await handler(call) : handler;
    return toResponse(result?.isReply ? result : reply(200, result));
  });
  vi.stubGlobal("fetch", fetcher);
  return calls;
}

/** Vencimiento cercano: setTimeout no admite plazos mayores a ~24,8 días. */
export const inOneHour = () => new Date(Date.now() + 60 * 60 * 1000).toISOString();

/** Inicia una sesión en memoria como lo hace el login real. */
export function signIn({
  role = "ADMINISTRADOR",
  permissions = ["USUARIOS_LEER", "USUARIOS_ESCRIBIR", "MATRICULA_LEER"],
  ...user
} = {}) {
  const session = {
    accessToken: "test-token",
    expiresAt: inOneHour(),
    user: {
      id: "1",
      username: "admin",
      fullName: "Ana Ramos",
      role,
      permissions,
      requiresPasswordChange: false,
      ...user,
    },
  };
  saveSession(session);
  return session;
}

export const signOut = clearSession;
