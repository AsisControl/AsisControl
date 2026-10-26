import { beforeEach, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, inOneHour, page, reply, signIn, signOut } from "../../../tests/support/backend.js";
import { currentUrl, setUrl } from "../../../tests/support/navigation.js";
import { AppShell } from "@/core/layout/app-shell";
import { ModuleOutlet } from "@/core/navigation/module-outlet";
import { setSessionStatus } from "@/core/auth/session-storage";
import AppLayout from "@/app/(app)/layout";
import AppPage from "@/app/(app)/[[...ruta]]/page";

vi.mock("next/navigation", () => import("../../../tests/support/navigation.js"));
vi.mock("next/link", () => import("../../../tests/support/next-link.jsx"));

const summary = (overrides = {}) => ({
  id: 1,
  username: "admin",
  nombreCompleto: "Ana Ramos",
  rol: "ADMINISTRADOR",
  permisos: ["USUARIOS_LEER", "MATRICULA_LEER"],
  requiereCambioContrasena: false,
  ...overrides,
});

function backend(routes = {}) {
  return fakeBackend({
    "GET /api/auth/me": summary(),
    "POST /api/auth/logout": reply(204),
    "GET /api/solicitudes-matricula": page([]),
    ...routes,
  });
}

const renderApp = () =>
  render(
    <AppShell>
      <ModuleOutlet />
    </AppShell>,
  );

const sessionFor = (overrides = {}) =>
  signIn({
    fullName: "Ana Ramos",
    permissions: ["USUARIOS_LEER", "MATRICULA_LEER"],
    ...overrides,
  });

beforeEach(() => {
  signOut();
  setUrl("/");
});

test("Sin sesión muestra el login, informa credenciales inválidas y luego ingresa", async () => {
  const calls = backend({
    "POST /api/auth/login": ({ body }) =>
      body.password === "Correcta2026"
        ? { accessToken: "nuevo", expiresAt: inOneHour(), user: summary() }
        : reply(401, { message: "Usuario o contraseña incorrectos." }),
  });
  const user = userEvent.setup();
  renderApp();
  expect(await screen.findByText("Iniciar sesión", { selector: "h2" })).toBeTruthy();
  expect(screen.getByText("¿Olvidaste tu contraseña?").getAttribute("href")).toBe(
    "/recuperar-contrasena",
  );
  await user.type(screen.getByLabelText(/^Usuario/), "admin");
  await user.type(screen.getByPlaceholderText("Ingresa tu contraseña"), "Incorrecta1");
  await user.click(screen.getByLabelText("Mostrar contraseña"));
  expect(screen.getByPlaceholderText("Ingresa tu contraseña").type).toBe("text");
  await user.click(screen.getByText("Iniciar sesión", { selector: "button" }));
  expect((await screen.findByRole("alert")).textContent).toBe("Usuario o contraseña incorrectos.");
  const password = screen.getByPlaceholderText("Ingresa tu contraseña");
  await user.clear(password);
  await user.type(password, "Correcta2026");
  await user.click(screen.getByText("Iniciar sesión", { selector: "button" }));
  expect(await screen.findByText("Panel de administración")).toBeTruthy();
  expect(calls.filter((c) => c.path === "/api/auth/login").at(-1).body).toEqual({
    identifier: "admin",
    password: "Correcta2026",
  });
});

test("El menú muestra solo las opciones permitidas y navega por URL", async () => {
  backend({ "GET /api/auth/me": summary({ permisos: ["USUARIOS_LEER"] }) });
  sessionFor({ permissions: ["USUARIOS_LEER"] });
  const user = userEvent.setup();
  renderApp();
  const nav = await screen.findByRole("navigation", { name: "Navegación principal" });
  expect(within(nav).queryByText("Matrícula")).toBeNull();
  expect(within(nav).getByText("Inicio").getAttribute("aria-current")).toBe("page");
  await user.click(within(nav).getByText("Finanzas"));
  expect(currentUrl()).toBe("/finanzas");
  expect(await screen.findByText("Este módulo todavía está pendiente de implementación.")).toBeTruthy();
  expect(within(nav).getByText("Finanzas").getAttribute("aria-current")).toBe("page");
});

test("Cada rol ve su menú y su pantalla de inicio", async () => {
  for (const [role, rol, title] of [
    ["DOCENTE", "DOCENTE", "Bienvenido/a, Ana Ramos"],
    ["ESTUDIANTE", "ALUMNO", "Bienvenido/a, Ana Ramos"],
    ["APODERADO", "APODERADO", "Panel del Apoderado"],
  ]) {
    backend({ "GET /api/auth/me": summary({ rol, permisos: [] }) });
    sessionFor({ role, permissions: [] });
    const { unmount } = renderApp();
    expect(await screen.findByText(title)).toBeTruthy();
    expect(screen.getByText(/Has ingresado como/)).toBeTruthy();
    unmount();
  }
});

test("Rutas desconocidas, sin permiso y direcciones antiguas", async () => {
  backend({ "GET /api/auth/me": summary({ permisos: [] }) });
  sessionFor({ permissions: [] });
  const user = userEvent.setup();
  setUrl("/no-existe");
  const { rerender } = renderApp();
  expect(await screen.findByText("PÁGINA NO ENCONTRADA")).toBeTruthy();
  await user.click(screen.getByText("Ir al inicio"));
  expect(currentUrl()).toBe("/");
  setUrl("/administracion/usuarios");
  rerender(
    <AppShell>
      <ModuleOutlet />
    </AppShell>,
  );
  expect(await screen.findByText("ACCESO DENEGADO")).toBeTruthy();
  setUrl("/estudiantes");
  await waitFor(() => expect(currentUrl()).toBe("/matricula"));
});

test("La URL abre la pantalla del módulo según el rol", async () => {
  backend({
    "GET /api/auth/me": summary({ rol: "APODERADO", permisos: ["MATRICULA_LEER"] }),
    "GET /api/familia/estudiantes": [],
    "GET /api/familia/oferta": [],
  });
  sessionFor({ role: "APODERADO", permissions: ["MATRICULA_LEER"] });
  setUrl("/matricula");
  renderApp();
  expect(await screen.findByText("Solicitudes de mis estudiantes")).toBeTruthy();
});

test("Administración recibe la pestaña desde la URL", async () => {
  backend({
    "GET /api/roles": [],
    "GET /api/roles/permisos": [],
  });
  sessionFor();
  setUrl("/administracion/roles-y-permisos");
  renderApp();
  expect(await screen.findByText("Permisos — Administrador")).toBeTruthy();
});

test("El menú de cuenta lleva a cambiar la contraseña y cierra la sesión", async () => {
  const calls = backend({ "POST /api/auth/change-password": reply(204) });
  sessionFor();
  const user = userEvent.setup();
  renderApp();
  await user.click(await screen.findByLabelText("Abrir menú de usuario"));
  await user.click(screen.getByText("Cambiar contraseña"));
  expect(currentUrl()).toBe("/cambiar-contrasena");
  expect(await screen.findByText("Confirma tu contraseña actual y elige una nueva.")).toBeTruthy();
  await user.click(screen.getByText("Cerrar sesión"));
  expect(await screen.findByText("Iniciar sesión", { selector: "h2" })).toBeTruthy();
  expect(calls.some((c) => c.path === "/api/auth/logout")).toBe(true);
  expect(currentUrl()).toBe("/");
});

test("Una contraseña temporal obliga a cambiarla antes de continuar", async () => {
  const calls = backend({
    "GET /api/auth/me": summary({ requiereCambioContrasena: true }),
    "POST /api/auth/change-password": ({ body }) =>
      body.currentPassword === "Mala" ? reply(400, { message: "La contraseña actual no es correcta." }) : reply(204),
  });
  sessionFor({ requiresPasswordChange: true });
  const user = userEvent.setup();
  renderApp();
  expect(await screen.findByText("Debes reemplazar tu contraseña temporal para continuar.")).toBeTruthy();
  const fill = async (current, next, confirm) => {
    for (const [label, value] of [
      [/Contraseña actual/, current],
      [/^Nueva contraseña/, next],
      [/Confirmar nueva contraseña/, confirm],
    ]) {
      const input = screen.getByLabelText(label);
      await user.clear(input);
      await user.type(input, value);
    }
    await user.click(screen.getByText("Guardar contraseña"));
  };
  await fill("Temporal1", "Nueva2026x", "Otra2026x");
  expect(screen.getByRole("alert").textContent).toBe("Las contraseñas nuevas no coinciden.");
  await fill("Temporal1", "corta", "corta");
  expect(screen.getByRole("alert").textContent).toMatch(/8 a 64 caracteres/);
  await fill("Mala", "Nueva2026x", "Nueva2026x");
  expect((await screen.findByRole("alert")).textContent).toBe("La contraseña actual no es correcta.");
  await fill("Temporal1", "Nueva2026x", "Nueva2026x");
  expect(await screen.findByText("Contraseña actualizada", { selector: "h1" })).toBeTruthy();
  expect(calls.filter((c) => c.path === "/api/auth/change-password").at(-1).body).toEqual({
    currentPassword: "Temporal1",
    newPassword: "Nueva2026x",
  });
  await user.click(screen.getByText("Ir al inicio de sesión"));
  expect(await screen.findByText("Iniciar sesión", { selector: "h2" })).toBeTruthy();
});

test("Si no se puede comprobar la sesión se avisa y se puede reintentar", async () => {
  let failing = true;
  backend({
    "GET /api/auth/me": () => (failing ? reply(503, { message: "Servicio en mantenimiento" }) : summary()),
  });
  sessionFor();
  const user = userEvent.setup();
  renderApp();
  const alert = await screen.findByRole("alert");
  expect(alert.textContent).toMatch(/No se pudo comprobar la sesión\..*Servicio en mantenimiento/);
  failing = false;
  await user.click(within(alert).getByText("Reintentar"));
  await waitFor(() => expect(screen.queryByRole("alert")).toBeNull());
});

test("Sin conexión con el backend la sesión muestra un mensaje claro", async () => {
  fakeBackend();
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => {
      throw new TypeError("Failed to fetch");
    }),
  );
  sessionFor();
  renderApp();
  expect((await screen.findByRole("alert")).textContent).toMatch(/Comprueba tu conexión/);
});

test("Un token revocado (401) devuelve al login", async () => {
  backend({ "GET /api/auth/me": reply(401, null) });
  sessionFor();
  renderApp();
  expect(await screen.findByText("Iniciar sesión", { selector: "h2" })).toBeTruthy();
});

test("Una sesión vencida no se muestra y otra pestaña puede cerrar la sesión", async () => {
  backend();
  sessionFor({ expiresAt: undefined });
  signIn({ fullName: "Ana Ramos" });
  const expired = signIn({ fullName: "Ana Ramos" });
  expired.expiresAt = "2000-01-01T00:00:00Z";
  renderApp();
  expect(await screen.findByText("Iniciar sesión", { selector: "h2" })).toBeTruthy();
  sessionFor();
  await act(async () => {
    window.dispatchEvent(new Event("pageshow"));
  });
  expect(await screen.findByText("Panel de administración")).toBeTruthy();
  await act(async () => {
    window.localStorage.setItem("asiscontrol:session-invalidation", "otra-pestaña");
    window.dispatchEvent(new Event("storage"));
  });
  expect(await screen.findByText("Iniciar sesión", { selector: "h2" })).toBeTruthy();
});

test("Volver a la pestaña revalida la sesión y la actividad la mantiene", async () => {
  const calls = backend();
  sessionFor();
  renderApp();
  await screen.findByText("Panel de administración");
  const before = calls.filter((c) => c.path === "/api/auth/me").length;
  await act(async () => {
    document.dispatchEvent(new Event("visibilitychange"));
    window.dispatchEvent(new Event("keydown"));
  });
  await waitFor(() =>
    expect(calls.filter((c) => c.path === "/api/auth/me").length).toBeGreaterThan(before),
  );
});

test("El menú móvil se abre y se cierra con Escape, clic fuera o al ensanchar", async () => {
  backend();
  sessionFor();
  const user = userEvent.setup();
  renderApp();
  const toggle = await screen.findByLabelText("Abrir menú de navegación");
  await user.click(toggle);
  expect(toggle.getAttribute("aria-expanded")).toBe("true");
  await user.keyboard("{Escape}");
  expect(toggle.getAttribute("aria-expanded")).toBe("false");
  await user.click(toggle);
  fireEvent.pointerDown(document.body);
  expect(toggle.getAttribute("aria-expanded")).toBe("false");
  await user.click(toggle);
  window.innerWidth = 1200;
  fireEvent(window, new Event("resize"));
  expect(toggle.getAttribute("aria-expanded")).toBe("false");
  await user.click(toggle);
  await user.click(screen.getByLabelText("Cerrar menú de navegación"));
  expect(toggle.getAttribute("aria-expanded")).toBe("false");
});

test("Si el servidor no responde al cerrar sesión, igual se cierra en la pestaña", async () => {
  backend({ "POST /api/auth/logout": reply(500, null) });
  sessionFor();
  const user = userEvent.setup();
  renderApp();
  await user.click(await screen.findByLabelText("Abrir menú de usuario"));
  await user.click(screen.getByText("Cerrar sesión"));
  expect(await screen.findByText("Iniciar sesión", { selector: "h2" })).toBeTruthy();
});

test("La ruta interna de Next monta el shell y la pantalla de la URL", async () => {
  backend();
  sessionFor();
  setUrl("/matricula");
  render(
    <AppLayout>
      <AppPage />
    </AppLayout>,
  );
  expect(await screen.findByText("Gestión de Matrícula")).toBeTruthy();
});

test("Académico exige ACADEMICO_LEER y la escritura habilita la creación", async () => {
  backend({
    "GET /api/auth/me": summary({ permisos: ["ACADEMICO_LEER"] }),
    "GET /api/cursos": page([]),
    "GET /api/cursos-competencias": page([]),
  });
  sessionFor({ permissions: ["ACADEMICO_LEER"] });
  setUrl("/academico");
  renderApp();
  expect(await screen.findByText("Gestión Curricular")).toBeTruthy();
  expect(await screen.findByText("Aún no hay cursos registrados.")).toBeTruthy();
  expect(screen.queryByText("+ Nuevo curso")).toBeNull();
});

test("Horarios exige ACADEMICO_LEER y abre la pestaña Aulas", async () => {
  backend({
    "GET /api/auth/me": summary({ permisos: ["ACADEMICO_LEER"] }),
    "GET /api/aulas": page([]),
    "GET /api/aulas/resumen": { habilitadas: 0 },
  });
  sessionFor({ permissions: ["ACADEMICO_LEER"] });
  setUrl("/horarios");
  renderApp();
  expect(await screen.findByText("Horario Académico")).toBeTruthy();
  expect(screen.getByText("Aulas", { selector: "button" }).getAttribute("aria-current")).toBe("page");
  expect(await screen.findByText("Aún no hay aulas registradas.")).toBeTruthy();
  expect(screen.queryByText("+ Nueva aula")).toBeNull();
});

/** Respuesta de login/refresh con un token que vence en `seconds`. */
const authResponse = (accessToken, seconds = 900) => ({
  accessToken,
  expiresAt: new Date(Date.now() + seconds * 1000).toISOString(),
  user: summary({ permisos: ["ACADEMICO_LEER"] }),
});
/** Llamadas reales a fetch con su método, credenciales y token. */
const fetchCalls = (path) =>
  fetch.mock.calls
    .filter(([url]) => new URL(url).pathname === path)
    .map(([, options = {}]) => ({
      method: options.method ?? "GET",
      credentials: options.credentials,
      authorization: new Headers(options.headers).get("Authorization"),
    }));
/** Como al abrir o recargar la página: sin sesión en memoria y comprobando. */
const reloadPage = () => setSessionStatus("checking");

test("Al recargar la página la cookie de renovación recupera la sesión", async () => {
  backend({ "POST /api/auth/refresh": authResponse("renovado") });
  reloadPage();
  renderApp();
  expect(screen.getByText("Cargando sesión…")).toBeTruthy();
  // pageshow llega junto con el montaje: no debe enviarse la cookie dos veces.
  window.dispatchEvent(new Event("pageshow"));
  expect(await screen.findByText("Panel de administración")).toBeTruthy();
  expect(fetchCalls("/api/auth/refresh")).toEqual([
    { method: "POST", credentials: "include", authorization: null },
  ]);
});

test("Sin cookie válida muestra el login sin cerrar las otras pestañas", async () => {
  backend({ "POST /api/auth/refresh": reply(401, { message: "Sesion invalida" }) });
  const mark = window.localStorage.getItem("asiscontrol:session-invalidation");
  reloadPage();
  renderApp();
  expect(await screen.findByText("Iniciar sesión", { selector: "h2" })).toBeTruthy();
  expect(window.localStorage.getItem("asiscontrol:session-invalidation")).toBe(mark);
  await act(async () => {
    window.dispatchEvent(new Event("pageshow"));
  });
  expect(fetchCalls("/api/auth/refresh")).toHaveLength(1);
});

test("Si no hay conexión al recargar se muestra el login y se reintenta al volver", async () => {
  let offline = true;
  backend({ "POST /api/auth/refresh": () => authResponse("renovado") });
  const backendFetch = fetch;
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url, options) => {
      if (offline) throw new TypeError("Failed to fetch");
      return backendFetch(url, options);
    }),
  );
  reloadPage();
  renderApp();
  expect(await screen.findByText("Iniciar sesión", { selector: "h2" })).toBeTruthy();
  offline = false;
  await act(async () => {
    window.dispatchEvent(new Event("pageshow"));
  });
  expect(await screen.findByText("Panel de administración")).toBeTruthy();
});

test("Renueva el token un minuto antes de que venza y usa el nuevo", async () => {
  backend({
    "POST /api/auth/refresh": () => authResponse("renovado"),
    "GET /api/cursos": page([]),
    "GET /api/cursos-competencias": page([]),
  });
  sessionFor({ permissions: ["ACADEMICO_LEER"] }).expiresAt = new Date(Date.now() + 60_300).toISOString();
  renderApp();
  await screen.findByText("Panel de administración");
  await waitFor(() => expect(fetchCalls("/api/auth/refresh")).toHaveLength(1), { timeout: 3000 });
  act(() => setUrl("/academico"));
  await screen.findByText("Aún no hay cursos registrados.");
  expect(fetchCalls("/api/cursos").at(-1).authorization).toBe("Bearer renovado");
});

test("Un 401 renueva el token y repite la petición una sola vez", async () => {
  let attempts = 0;
  backend({
    "GET /api/auth/me": summary({ permisos: ["ACADEMICO_LEER"] }),
    "POST /api/auth/refresh": authResponse("renovado"),
    "GET /api/cursos": () => (++attempts === 1 ? reply(401, null) : page([])),
    "GET /api/cursos-competencias": page([]),
  });
  sessionFor({ permissions: ["ACADEMICO_LEER"] });
  setUrl("/academico");
  renderApp();
  expect(await screen.findByText("Aún no hay cursos registrados.")).toBeTruthy();
  expect(fetchCalls("/api/cursos").map((c) => c.authorization)).toEqual(["Bearer test-token", "Bearer renovado"]);
  expect(fetchCalls("/api/auth/refresh")).toHaveLength(1);
});

test("Si la renovación es rechazada, el 401 devuelve al login", async () => {
  backend({
    "GET /api/auth/me": summary({ permisos: ["ACADEMICO_LEER"] }),
    "POST /api/auth/refresh": reply(401, null),
    "GET /api/cursos": reply(401, null),
    "GET /api/cursos-competencias": page([]),
  });
  sessionFor({ permissions: ["ACADEMICO_LEER"] });
  setUrl("/academico");
  renderApp();
  expect(await screen.findByText("Iniciar sesión", { selector: "h2" })).toBeTruthy();
  expect(fetchCalls("/api/cursos")).toHaveLength(1);
});

test("Un token vencido al volver a la pestaña se renueva en lugar de cerrar la sesión", async () => {
  backend({ "POST /api/auth/refresh": authResponse("renovado") });
  sessionFor();
  renderApp();
  await screen.findByText("Panel de administración");
  sessionFor().expiresAt = "2000-01-01T00:00:00Z";
  await act(async () => {
    document.dispatchEvent(new Event("visibilitychange"));
  });
  expect(await screen.findByText("Panel de administración")).toBeTruthy();
  expect(fetchCalls("/api/auth/refresh")).toHaveLength(1);
});

test("Con Web Locks las pestañas renuevan por turnos", async () => {
  backend({ "POST /api/auth/refresh": authResponse("renovado") });
  const request = vi.fn((_name, task) => task());
  Object.defineProperty(navigator, "locks", { value: { request }, configurable: true });
  try {
    reloadPage();
    renderApp();
    expect(await screen.findByText("Panel de administración")).toBeTruthy();
    expect(request).toHaveBeenCalledTimes(1);
    expect(request.mock.calls[0][0]).toBe("asiscontrol:session-refresh");
  } finally {
    delete navigator.locks;
  }
});
