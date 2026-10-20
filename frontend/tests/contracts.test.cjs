/* eslint-disable @typescript-eslint/no-require-imports -- Node test harness loads isolated TypeScript modules. */
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const ts = require("typescript");
function harness(fetcher) {
  const cache = {};
  const root = path.join(__dirname, "..");
  // Resuelve como el bundler: archivo .ts/.tsx o carpeta con index.ts/.tsx.
  function resolve(base) {
    const candidates = [
      base,
      `${base}.ts`,
      `${base}.tsx`,
      path.join(base, "index.ts"),
      path.join(base, "index.tsx"),
    ];
    const found = candidates.find(
      (c) => fs.existsSync(c) && fs.statSync(c).isFile(),
    );
    if (!found) throw new Error(`No se encontró el módulo ${base}`);
    return found;
  }
  function load(name) {
    const file = resolve(path.isAbsolute(name) ? name : path.join(root, name));
    if (cache[file]) return cache[file].exports;
    const loadedModule = { exports: {} };
    cache[file] = loadedModule;
    const code = ts.transpileModule(fs.readFileSync(file, "utf8"), {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2022,
        jsx: ts.JsxEmit.ReactJSX,
      },
    }).outputText;
    const requireLocal = (id) => {
      if (id.startsWith("@/")) return load(path.join(root, "src", id.slice(2)));
      if (id.startsWith("./") || id.startsWith("../"))
        return load(path.join(path.dirname(file), id));
      return require(id);
    };
    vm.runInNewContext(code, {
      module: loadedModule,
      exports: loadedModule.exports,
      require: requireLocal,
      process: { env: { NEXT_PUBLIC_API_BASE_URL: "http://api.test" } },
      fetch: fetcher,
      TextEncoder,
      Headers,
      AbortController,
      setTimeout,
      clearTimeout,
      crypto: require("node:crypto").webcrypto,
    });
    return loadedModule.exports;
  }
  return load;
}
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
  const result = await load(
    "src/core/auth/http-auth-service.ts",
  ).httpAuthService.login({ username: " alumno ", password: "Temporal123!" });
  assert.equal(sent.url, "http://api.test/api/auth/login");
  assert.deepEqual(sent.body, {
    identifier: "alumno",
    password: "Temporal123!",
  });
  assert.equal(result.ok, true);
  assert.equal(result.session.user.role, "ESTUDIANTE");
  assert.equal(result.session.user.requiresPasswordChange, true);
});
test("Acepta los otros tres roles institucionales", () => {
  const auth = harness()("src/core/auth/http-auth-service.ts");
  for (const role of ["ADMINISTRADOR", "DOCENTE", "APODERADO"])
    assert.equal(auth.mapAuth(backendAuth(role)).user.role, role);
});
test("Rechaza un rol ajeno a la interfaz", () => {
  const auth = harness()("src/core/auth/http-auth-service.ts");
  assert.throws(
    () => auth.mapAuth(backendAuth("TESORERIA")),
    /rol.*no está habilitado/,
  );
});
test("401 elimina la sesión en memoria", async () => {
  const load = harness(async () =>
    response(401, { message: "Sesión vencida" }),
  );
  const storage = load("src/core/auth/session-storage.ts");
  storage.saveSession({ accessToken: "test-token" });
  await assert.rejects(
    load("src/core/api/client.ts").api("/api/usuarios"),
    /Sesión vencida/,
  );
  assert.equal(storage.readSession(), null);
});
test("403 conserva la sesión y no añade códigos de referencia", async () => {
  const load = harness(async () =>
    response(403, { message: "Acceso denegado", code: "INTERNO_123" }),
  );
  const storage = load("src/core/auth/session-storage.ts");
  storage.saveSession({ accessToken: "test-token" });
  await assert.rejects(
    load("src/core/api/client.ts").api("/api/usuarios"),
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
  load("src/core/auth/session-storage.ts").saveSession({
    accessToken: "test-token",
  });
  await load("src/core/api/client.ts").api("/api/usuarios", {
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
  const storage = load("src/core/auth/session-storage.ts");
  storage.saveSession({ accessToken: "test-token" });
  await load("src/core/auth/http-auth-service.ts").httpAuthService.logout();
  assert.deepEqual(called, ["http://api.test/api/auth/logout", "POST"]);
  assert.equal(storage.readSession(), null);
});
test("Generación temporal cumple requisitos y varía entre llamadas", () => {
  const { temporaryPassword } = harness()("src/modules/administration/create-access.tsx");
  const values = Array.from({ length: 100 }, temporaryPassword);
  for (const value of values)
    assert.match(value, /^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])[!-~]{16}$/);
  assert.equal(new Set(values).size, 100);
});

test("Normaliza las tres respuestas paginadas del backend sin perder filas", () => {
  const { normalizePage } = harness()("src/shared/lib/pagination.ts");
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
test("Una página vacía no permite avanzar a páginas inexistentes", () => {
  const result = harness()("src/shared/lib/pagination.ts").normalizePage({
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
  load("src/core/auth/session-storage.ts").saveSession({
    accessToken: "test-token",
  });
  await load("src/core/api/client.ts").api(
    "/api/auth/recovery/request",
    { method: "POST", body: JSON.stringify({ identifier: "usuario" }) },
    false,
  );
  assert.equal(headers.has("Authorization"), false);
});
test("La política admite ocho caracteres y rechaza usuario, espacios y exceso de bytes", () => {
  const policy = harness()(
    "src/core/auth/password-policy.ts",
  ).passwordError;
  assert.equal(policy("Abcdefg1", "usuario"), "");
  assert.equal(policy("Ábcdefg1", "usuario"), "");
  assert.notEqual(policy("Usuario1", "Usuario1"), "");
  assert.notEqual(policy("Abc defg1", "usuario"), "");
  assert.notEqual(policy("abcdefgh", "usuario"), "");
  assert.notEqual(policy("Á".repeat(60) + "ab1", "usuario"), "");
});
