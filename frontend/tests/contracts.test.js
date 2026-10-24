// @vitest-environment node
import { test } from "vitest";
import assert from "node:assert/strict";
import { harness } from "./support/load-module.js";
const response = (status, body) => ({
  status,
  ok: status >= 200 && status < 300,
  json: async () => body,
});
const backendAuth = (role) => ({
  accessToken: "test-token",
  expiresAt: "2099-01-01T00:00:00Z",
  user: {
    id: 42,
    username: "demo",
    nombreCompleto: "Persona Prueba",
    rol: role,
    permisos: ["MATRICULA_LEER"],
    requiereCambioContrasena: true,
  },
});
test("Login usa identifier y transforma ALUMNO a ESTUDIANTE", async () => {
  let sent;
  const load = harness(async (url, options) => {
    sent = { url, body: JSON.parse(options.body) };
    return response(200, backendAuth("ALUMNO"));
  });
  const result = await (await load("src/core/auth/http-auth-service.ts")).httpAuthService.login({ username: " alumno ", password: "Temporal123!" });
  assert.equal(sent.url, "http://api.test/api/auth/login");
  assert.deepEqual(sent.body, {
    identifier: "alumno",
    password: "Temporal123!",
  });
  assert.equal(result.ok, true);
  assert.equal(result.session.user.role, "ESTUDIANTE");
  assert.equal(result.session.user.requiresPasswordChange, true);
});
test("Acepta los otros tres roles institucionales", async () => {
  const auth = (await harness()("src/core/auth/http-auth-service.ts"));
  for (const role of ["ADMINISTRADOR", "DOCENTE", "APODERADO"])
    assert.equal(auth.mapAuth(backendAuth(role)).user.role, role);
});
test("Rechaza un rol ajeno a la interfaz", async () => {
  const auth = (await harness()("src/core/auth/http-auth-service.ts"));
  assert.throws(
    () => auth.mapAuth(backendAuth("TESORERIA")),
    /rol.*no está habilitado/,
  );
});
test("401 elimina la sesión en memoria", async () => {
  const load = harness(async () =>
    response(401, { message: "Sesión vencida" }),
  );
  const storage = (await load("src/core/auth/session-storage.ts"));
  storage.saveSession({ accessToken: "test-token" });
  await assert.rejects(
    (await load("src/core/api/client.ts")).api("/api/usuarios"),
    /Sesión vencida/,
  );
  assert.equal(storage.readSession(), null);
});
test("403 conserva la sesión y no añade códigos de referencia", async () => {
  const load = harness(async () =>
    response(403, { message: "Acceso denegado", code: "INTERNO_123" }),
  );
  const storage = (await load("src/core/auth/session-storage.ts"));
  storage.saveSession({ accessToken: "test-token" });
  await assert.rejects(
    (await load("src/core/api/client.ts")).api("/api/usuarios"),
    (e) => e.message === "Acceso denegado",
  );
  assert.equal(storage.readSession().accessToken, "test-token");
});
test("Envia Bearer y JSON al backend", async () => {
  let headers;
  const load = harness(async (_, options) => {
    headers = options.headers;
    return response(204, null);
  });
  (await load("src/core/auth/session-storage.ts")).saveSession({
    accessToken: "test-token",
  });
  await (await load("src/core/api/client.ts")).api("/api/usuarios", {
    method: "POST",
    body: "{}",
  });
  assert.equal(headers.get("Authorization"), "Bearer test-token");
  assert.equal(headers.get("Content-Type"), "application/json");
});
test("Logout revoca en servidor y limpia la sesión", async () => {
  let called;
  const load = harness(async (url, options) => {
    called = [url, options.method];
    return response(204, null);
  });
  const storage = (await load("src/core/auth/session-storage.ts"));
  storage.saveSession({ accessToken: "test-token" });
  await (await load("src/core/auth/http-auth-service.ts")).httpAuthService.logout();
  assert.deepEqual(called, ["http://api.test/api/auth/logout", "POST"]);
  assert.equal(storage.readSession(), null);
});
test("Generación temporal cumple requisitos y varía entre llamadas", async () => {
  const { temporaryPassword } = (await harness()("src/modules/administration/create-access.tsx"));
  const values = Array.from({ length: 100 }, temporaryPassword);
  for (const value of values)
    assert.match(value, /^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])[!-~]{16}$/);
  assert.equal(new Set(values).size, 100);
});

test("Normaliza las tres respuestas paginadas del backend sin perder filas", async () => {
  const { normalizePage } = (await harness()("src/shared/lib/pagination.ts"));
  for (const raw of [
    { content: [{ id: 42 }], page: 1, totalElements: 21, totalPages: 2 },
    { content: [{ id: 42 }], number: 1, totalElements: 21, totalPages: 2 },
    {
      content: [{ id: 42 }],
      page: { number: 1, size: 20, totalElements: 21, totalPages: 2 },
    },
  ]) {
    const result = normalizePage(raw);
    assert.equal(result.page, 1);
    assert.equal(result.totalElements, 21);
    assert.equal(result.totalPages, 2);
    assert.equal(result.first, false);
    assert.equal(result.last, true);
    assert.equal(result.content[0].id, 42);
  }
});
test("Una página vacía no permite avanzar a páginas inexistentes", async () => {
  const result = (await harness()("src/shared/lib/pagination.ts")).normalizePage({
    content: [],
    page: 0,
    totalElements: 0,
    totalPages: 0,
  });
  assert.equal(result.first, true);
  assert.equal(result.last, true);
  assert.equal(result.totalElements, 0);
});

test("La recuperación pública no envía el token de una sesión existente", async () => {
  let headers;
  const load = harness(async (url, options) => {
    headers = options.headers;
    return response(202, { message: "Respuesta genérica" });
  });
  (await load("src/core/auth/session-storage.ts")).saveSession({
    accessToken: "test-token",
  });
  await (await load("src/core/api/client.ts")).api(
    "/api/auth/recovery/request",
    { method: "POST", body: JSON.stringify({ identifier: "usuario" }) },
    false,
  );
  assert.equal(headers.has("Authorization"), false);
});
test("La política admite ocho caracteres y rechaza usuario, espacios y exceso de bytes", async () => {
  const policy = (await harness()("src/core/auth/password-policy.ts")).passwordError;
  assert.equal(policy("Abcdefg1", "usuario"), "");
  assert.equal(policy("Ábcdefg1", "usuario"), "");
  assert.notEqual(policy("Usuario1", "Usuario1"), "");
  assert.notEqual(policy("Abc defg1", "usuario"), "");
  assert.notEqual(policy("abcdefgh", "usuario"), "");
  assert.notEqual(policy("Á".repeat(60) + "ab1", "usuario"), "");
});
test("El menú oculta opciones sin permiso y la creación de usuarios exige escritura", async () => {
  const { canAccess } = (await harness()("src/core/navigation/access-policy.ts"));
  const admin = (permissions) => ({
    id: "1",
    username: "admin",
    fullName: "Admin",
    role: "ADMINISTRADOR",
    permissions,
  });
  const reader = admin(["MATRICULA_LEER", "USUARIOS_LEER"]);
  assert.equal(canAccess(reader, "Matrícula"), true);
  assert.equal(canAccess(reader, "Administración"), true);
  assert.equal(canAccess(reader, "Administración", ["usuarios", "nuevo"]), false);
  assert.equal(
    canAccess(admin(["USUARIOS_LEER", "USUARIOS_ESCRIBIR"]), "Administración", [
      "usuarios",
      "nuevo",
    ]),
    true,
  );
  assert.equal(canAccess(admin([]), "Matrícula"), false);
  assert.equal(canAccess(admin([]), "Asistencia"), true);
  assert.equal(
    canAccess({ ...reader, role: "DOCENTE" }, "Administración"),
    false,
  );
});
test("Cerrar sesión deja una marca para las otras pestañas sin guardar el token", async () => {
  const stored = {};
  const listeners = [];
  const window = {
    localStorage: {
      getItem: (key) => stored[key] ?? null,
      setItem: (key, value) => {
        stored[key] = value;
      },
    },
    dispatchEvent: () => true,
  };
  const storage = (await harness(undefined, { window, Event })("src/core/auth/session-storage.ts"));
  storage.subscribeSession(() => listeners.push(storage.readSession()));
  storage.saveSession({ accessToken: "test-token", user: {} });
  storage.clearSession();
  assert.equal(storage.readSession(), null);
  assert.equal(Object.values(stored).join(" ").includes("test-token"), false);
  assert.ok(stored["asiscontrol:session-invalidation"]);
  assert.equal(listeners.at(-1), null);
});
