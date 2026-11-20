import { act, fireEvent, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { roleContextEventKey, renewSession } from "@/core/auth/session-storage";
import { useAuth } from "@/core/auth/use-auth";
import { fakeBackend, inOneHour, reply, signIn, signOut } from "../../../tests/support/backend.js";

const invalidationKey = "asiscontrol:session-invalidation";
const roles = ["DOCENTE", "APODERADO"];
const credentials = { username: "20260001", password: "Correcta2026" };

function summary(overrides = {}) {
  return {
    id: 1,
    username: credentials.username,
    nombreCompleto: "Ana Ramos",
    rol: "DOCENTE",
    rolesDisponibles: roles,
    sesionId: 14,
    versionContexto: 0,
    permisos: ["ASISTENCIA_LEER"],
    requiereCambioContrasena: false,
    ...overrides,
  };
}

function guardianSummary() {
  return summary({ rol: "APODERADO", versionContexto: 1, permisos: ["PAGOS_LEER"] });
}

function response(user, accessToken = "token-docente", expiresAt = inOneHour()) {
  return { accessToken, expiresAt, user };
}

function backend(routes = {}) {
  return fakeBackend({
    "GET /api/auth/me": summary(),
    "POST /api/auth/login": () => response(summary()),
    "POST /api/auth/logout": reply(204),
    ...routes,
  });
}

function seed() {
  signIn({
    username: credentials.username,
    role: "DOCENTE",
    availableRoles: roles,
    sessionId: "14",
    contextVersion: 0,
  });
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((accept, deny) => {
    resolve = accept;
    reject = deny;
  });
  return { promise, resolve, reject };
}

function externalRoleChange() {
  window.dispatchEvent(new StorageEvent("storage", { key: roleContextEventKey }));
}

function requests(calls, path) {
  return calls.filter((call) => call.path === path);
}

beforeEach(() => {
  signOut();
  window.localStorage.removeItem(roleContextEventKey);
});

afterEach(() => {
  vi.useRealTimers();
});

test("Una pestaña que cerró sesión no renueva al recibir un anuncio de rol", async () => {
  const calls = backend();
  const { result } = renderHook(useAuth);
  await act(async () => externalRoleChange());
  expect(result.current.session).toBeNull();
  expect(requests(calls, "/api/auth/refresh")).toHaveLength(0);
});

test("Al coincidir los avisos de cierre y cambio de rol prevalece el cierre", async () => {
  seed();
  const calls = backend();
  const { result } = renderHook(useAuth);
  await act(async () => {
    window.localStorage.setItem(invalidationKey, "sesión cerrada en otra pestaña");
    externalRoleChange();
  });
  expect(result.current.session).toBeNull();
  expect(requests(calls, "/api/auth/refresh")).toHaveLength(0);
});

test.each(["responde con el token anterior", "falla por conexión"])(
  "Espera la renovación en curso cuando otra pestaña cambia de rol y esta %s",
  async (outcome) => {
    seed();
    const pending = deferred();
    const reload = vi.fn()
      .mockImplementationOnce(() => pending.promise)
      .mockReturnValueOnce(response(guardianSummary(), "token-apoderado"));
    const calls = backend({ "POST /api/auth/refresh": reload });
    const { result } = renderHook(useAuth);
    await act(async () => {
      void renewSession();
      externalRoleChange();
      externalRoleChange();
    });
    expect(result.current.contextSuspended).toBe(true);
    expect(requests(calls, "/api/auth/refresh")).toHaveLength(1);
    await act(async () => {
      if (outcome === "falla por conexión") {
        pending.reject(new TypeError("sin red"));
      } else {
        pending.resolve(response(summary(), "token-anterior"));
      }
    });
    expect(result.current.session?.accessToken).toBe("token-apoderado");
    expect(result.current.contextSuspended).toBe(false);
    expect(result.current.sessionValidationError).toBe("");
    expect(window.localStorage.getItem(roleContextEventKey)).toBeNull();
    expect(requests(calls, "/api/auth/refresh")).toHaveLength(2);
  },
);

test("Una renovación iniciada en esta pestaña anuncia el cambio de contexto que detectó", async () => {
  seed();
  backend({ "POST /api/auth/refresh": response(guardianSummary(), "token-apoderado") });
  const { result } = renderHook(useAuth);
  await act(async () => {
    expect(await renewSession()).toBe(true);
  });
  expect(result.current.session?.user.role).toBe("APODERADO");
  expect(window.localStorage.getItem(roleContextEventKey)).toBeTruthy();
});

test("Si /me trae otro contexto, espera un token de ese contexto antes de mostrarlo", async () => {
  seed();
  const pending = deferred();
  const calls = backend({
    "GET /api/auth/me": guardianSummary(),
    "POST /api/auth/refresh": () => pending.promise,
  });
  const { result } = renderHook(useAuth);
  await act(async () => {});
  expect(result.current.contextSuspended).toBe(true);
  expect(result.current.session?.user.role).toBe("DOCENTE");
  expect(requests(calls, "/api/auth/refresh")).toHaveLength(1);

  await act(async () => {
    pending.resolve(response(guardianSummary(), "token-apoderado"));
  });
  expect(result.current.session?.user.role).toBe("APODERADO");
  expect(result.current.session?.accessToken).toBe("token-apoderado");
  expect(result.current.contextSuspended).toBe(false);
});

test("Un cambio rechazado por contexto vencido revalida la sesión y permite reintentar", async () => {
  seed();
  const change = vi.fn()
    .mockReturnValueOnce(reply(401, { message: "El contexto ya cambió." }))
    .mockReturnValueOnce(response(guardianSummary(), "token-apoderado"));
  const renew = vi.fn()
    .mockReturnValueOnce(reply(503, { message: "No se pudo renovar la sesión." }))
    .mockReturnValueOnce(response(summary(), "token-renovado"));
  const calls = backend({
    "PUT /api/auth/rol-activo": change,
    "POST /api/auth/refresh": renew,
  });
  const { result } = renderHook(useAuth);
  await act(async () => {
    await expect(result.current.switchRole("APODERADO")).rejects.toMatchObject({ status: 401 });
  });
  expect(result.current.contextSuspended).toBe(true);
  expect(result.current.sessionValidationError).toBe("No se pudo renovar la sesión.");
  await act(async () => result.current.retrySession());
  expect(result.current.session?.accessToken).toBe("token-renovado");
  expect(result.current.contextSuspended).toBe(false);
  await act(async () => result.current.switchRole("APODERADO"));
  expect(result.current.session?.accessToken).toBe("token-apoderado");
  expect(requests(calls, "/api/auth/rol-activo").map((request) => request.body))
    .toEqual([{ rol: "APODERADO" }, { rol: "APODERADO" }]);
});

test("Si el servidor rechaza un rol por permisos, conserva el contexto y no renueva", async () => {
  seed();
  const calls = backend({
    "PUT /api/auth/rol-activo": reply(403, { message: "Ese rol no está disponible." }),
  });
  const { result } = renderHook(useAuth);
  await act(async () => {
    await expect(result.current.switchRole("APODERADO"))
      .rejects.toThrow("Ese rol no está disponible.");
  });
  expect(result.current.session?.user.role).toBe("DOCENTE");
  expect(result.current.contextSuspended).toBe(false);
  expect(requests(calls, "/api/auth/refresh")).toHaveLength(0);
});

test("Si el cambio esperó el bloqueo y se cerró la sesión, nunca envía la solicitud", async () => {
  seed();
  const waiting = deferred();
  const lock = vi.fn(async (_name, operation) => {
    await waiting.promise;
    return operation();
  });
  vi.stubGlobal("navigator", { locks: { request: lock } });
  const calls = backend();
  const { result } = renderHook(useAuth);
  await act(async () => {});
  const change = result.current.switchRole("APODERADO").catch((error) => error);
  await act(async () => {
    signOut();
    waiting.resolve();
  });
  expect(await change).toEqual(new Error("La sesión cambió. Revisa tus roles disponibles."));
  expect(requests(calls, "/api/auth/rol-activo")).toHaveLength(0);
  expect(lock).toHaveBeenCalledWith("asiscontrol:session-refresh", expect.any(Function));
});

test("Nunca aplica un cambio de rol que se resolvió después de pedir cerrar sesión", async () => {
  seed();
  const pendingChange = deferred();
  const pendingLogout = deferred();
  backend({
    "PUT /api/auth/rol-activo": () => pendingChange.promise,
    "POST /api/auth/logout": () => pendingLogout.promise,
  });
  const { result } = renderHook(useAuth);
  await act(async () => {});
  const change = result.current.switchRole("APODERADO");
  let logout;
  await act(async () => {
    logout = result.current.logout();
    pendingChange.resolve(response(guardianSummary(), "token-apoderado"));
    await change;
  });
  expect(result.current.isEndingSession).toBe(true);
  expect(result.current.session?.user.role).toBe("DOCENTE");
  expect(window.localStorage.getItem(roleContextEventKey)).toBeNull();
  await act(async () => {
    pendingLogout.resolve(reply(204));
    await logout;
  });
  expect(result.current.session).toBeNull();
});

test("Confirmar el mismo rol actualiza su token sin anunciar un cambio a otras pestañas", async () => {
  seed();
  backend({
    "PUT /api/auth/rol-activo": response(summary(), "token-del-mismo-contexto"),
  });
  const { result } = renderHook(useAuth);
  await act(async () => result.current.switchRole("DOCENTE"));
  expect(result.current.session?.accessToken).toBe("token-del-mismo-contexto");
  expect(window.localStorage.getItem(roleContextEventKey)).toBeNull();
});

test.each(["respuesta", "error"])(
  "Descarta la %s de /me del contexto docente después de cambiar a apoderado",
  async (outcome) => {
    seed();
    const pending = deferred();
    const calls = backend({
      "GET /api/auth/me": () => pending.promise,
      "PUT /api/auth/rol-activo": response(guardianSummary(), "token-apoderado"),
    });
    const { result } = renderHook(useAuth);
    await act(async () => {
      fireEvent(window, new Event("pageshow"));
      vi.spyOn(document, "visibilityState", "get").mockReturnValue("hidden");
      fireEvent(document, new Event("visibilitychange"));
    });
    expect(requests(calls, "/api/auth/me")).toHaveLength(1);
    await act(async () => result.current.switchRole("APODERADO"));
    await act(async () => {
      if (outcome === "error") {
        pending.reject(new Error("Error del contexto antiguo."));
      } else {
        pending.resolve(summary());
      }
    });
    expect(result.current.session?.accessToken).toBe("token-apoderado");
    expect(result.current.sessionValidationError).toBe("");
  },
);

test("Con un /me que supera el tiempo de espera informa el estado y deja reintentar", async () => {
  vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval"] });
  seed();
  const slow = vi.fn()
    .mockImplementationOnce((_url, { signal }) => new Promise((_resolve, reject) => {
      signal.addEventListener("abort", () => reject(Object.assign(new Error("Tiempo de espera"), { name: "AbortError" })), { once: true });
    }))
    .mockResolvedValueOnce({ ok: true, json: async () => summary() });
  vi.stubGlobal("fetch", slow);
  const { result } = renderHook(useAuth);
  await act(async () => vi.advanceTimersByTimeAsync(20_000));

  expect(result.current.sessionValidationError).toBe("El servidor tardó demasiado. Inténtalo nuevamente.");
  expect(result.current.session?.user.role).toBe("DOCENTE");
  await act(async () => result.current.retrySession());
  expect(result.current.sessionValidationError).toBe("");
});

test.each(["actividad", "pageshow", "intervalo"])(
  "Tras inactividad cierra la sesión al detectar %s sin renovar el token",
  async (event) => {
    vi.useFakeTimers();
    const calls = backend();
    const { result } = renderHook(useAuth);
    await act(async () => result.current.login(credentials));
    expect(result.current.session?.user.role).toBe("DOCENTE");
    await act(async () => {
      if (event === "intervalo") {
        await vi.advanceTimersByTimeAsync(30 * 60 * 1000);
      } else {
        vi.setSystemTime(Date.now() + 30 * 60 * 1000);
        fireEvent(window, new Event(event === "actividad" ? "keydown" : event));
      }
    });
    expect(result.current.session).toBeNull();
    expect(requests(calls, "/api/auth/refresh")).toHaveLength(0);
  },
);

test("Una interacción después de dos minutos revalida el rol sin esperar recargar la página", async () => {
  vi.useFakeTimers();
  const calls = backend({
    "GET /api/auth/me": summary({ permisos: ["ASISTENCIA_LEER", "USUARIOS_LEER"] }),
  });
  const { result } = renderHook(useAuth);
  await act(async () => result.current.login(credentials));
  vi.setSystemTime(Date.now() + 3 * 60 * 1000);
  await act(async () => fireEvent(window, new Event("pointerdown")));
  expect(requests(calls, "/api/auth/me")).toHaveLength(1);
  expect(result.current.session?.user.permissions).toContain("USUARIOS_LEER");
});

test("Interactuar con un token ya vencido inicia la renovación en lugar de revalidar /me", async () => {
  vi.useFakeTimers();
  const calls = backend({
    "POST /api/auth/login": () => response(
      summary(),
      "token-docente",
      new Date(Date.now() + 10 * 60 * 1000).toISOString(),
    ),
    "POST /api/auth/refresh": () => response(summary(), "token-renovado"),
  });
  const { result } = renderHook(useAuth);
  await act(async () => result.current.login(credentials));
  const expiresAt = Date.parse(result.current.session.expiresAt);
  vi.setSystemTime(expiresAt + 1_000);
  await act(async () => fireEvent(window, new Event("keydown")));
  expect(result.current.session?.accessToken).toBe("token-renovado");
  expect(requests(calls, "/api/auth/refresh")).toHaveLength(1);
});
test("El intervalo vuelve a renovar un token que venció tras fallar la renovación anticipada", async () => {
  vi.useFakeTimers();
  const renew = vi.fn()
    .mockReturnValueOnce(reply(503, { message: "Servidor temporalmente no disponible." }))
    .mockImplementationOnce(() => response(summary(), "token-recuperado"));
  const calls = backend({
    "POST /api/auth/login": () => response(
      summary(),
      "token-docente",
      new Date(Date.now() + 10_000).toISOString(),
    ),
    "POST /api/auth/refresh": renew,
  });
  const { result } = renderHook(useAuth);
  await act(async () => result.current.login(credentials));
  await act(async () => vi.advanceTimersByTimeAsync(1));
  expect(result.current.session?.accessToken).toBe("token-docente");
  expect(result.current.sessionValidationError).toBe("Servidor temporalmente no disponible.");
  await act(async () => vi.advanceTimersByTimeAsync(15_000));
  expect(result.current.session?.accessToken).toBe("token-recuperado");
  expect(result.current.sessionValidationError).toBe("");
  expect(requests(calls, "/api/auth/refresh")).toHaveLength(2);
});

test("Una renovación que se ejecuta al reanudar la pestaña respeta el límite de inactividad", async () => {
  vi.useFakeTimers();
  const calls = backend({
    "POST /api/auth/login": () => response(
      summary(),
      "token-docente",
      new Date(Date.now() + 61_000).toISOString(),
    ),
  });
  const { result } = renderHook(useAuth);
  await act(async () => result.current.login(credentials));
  vi.setSystemTime(Date.now() + 31 * 60 * 1000);
  await act(async () => vi.advanceTimersByTimeAsync(1000));
  expect(result.current.session).toBeNull();
  expect(requests(calls, "/api/auth/refresh")).toHaveLength(0);
});

test("Revalida una sesión que todavía no tiene una fecha de vencimiento", async () => {
  const calls = backend({
    "POST /api/auth/login": () => ({ accessToken: "token-docente", user: summary() }),
  });
  const { result } = renderHook(useAuth);
  await act(async () => result.current.login(credentials));
  await act(async () => result.current.retrySession());
  expect(result.current.session?.user.role).toBe("DOCENTE");
  expect(requests(calls, "/api/auth/me")).toHaveLength(1);
  expect(requests(calls, "/api/auth/refresh")).toHaveLength(0);
});

test("Si /me falla sin descripción, informa el error sin borrar la sesión", async () => {
  seed();
  backend({ "GET /api/auth/me": reply(502) });
  const { result } = renderHook(useAuth);
  await act(async () => {});
  expect(result.current.sessionValidationError).toBe("No se pudo comprobar la sesión.");
  expect(result.current.session?.user.role).toBe("DOCENTE");
});
