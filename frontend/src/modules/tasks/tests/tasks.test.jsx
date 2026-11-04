import { beforeEach, expect, test, vi } from "vitest";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { Tasks } from "@/modules/tasks";
import { listarTareas, obtenerTarea } from "@/modules/tasks/services/tarea-service";
import { ModuleOutlet } from "@/core/navigation/module-outlet";
import { SessionUserProvider } from "@/core/layout/session-user";
import { canAccess } from "@/core/navigation/access-policy";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const task = (overrides = {}) => ({
  id: 1, asignacionCursoId: 42, nombre: "Práctica de álgebra",
  descripcion: "Resuelve los ejercicios", fechaPublicacion: null,
  fechaLimite: "2026-11-02T18:30:00", puntajeMaximo: 20,
  maxArchivosEntrega: 2, estado: "BORRADOR", archivos: [], version: 0,
  ...overrides,
});
const assignment = (overrides = {}) => ({
  id: 42, cursoId: 5, curso: "Matemática", seccionId: 8, seccion: "2° B",
  anioAcademico: 2026, docenteId: 777, docente: "Ana Pérez", activo: true, version: 1,
  ...overrides,
});
function backend(routes = {}) {
  return fakeBackend({
    "GET /api/tareas": page([task()]),
    "GET /api/asignaciones-cursos/42": assignment(),
    ...routes,
  });
}
beforeEach(() => {
  signIn({ role: "DOCENTE", id: "999", permissions: ["TAREAS_LEER", "ACADEMICO_LEER"] });
  setUrl("/tareas");
});

test("Lista los cuatro estados reales y resuelve solo las asignaciones de la página", async () => {
  const states = ["BORRADOR", "PUBLICADA", "CERRADA", "ARCHIVADA"];
  const calls = backend({ "GET /api/tareas": page(states.map((estado, id) => task({ id, estado, nombre: `Tarea ${id}` }))) });
  render(<Tasks />);
  expect(screen.getByRole("status").textContent).toBe("Cargando tareas…");
  await screen.findAllByText("Matemática · 2° B · 2026");
  for (const label of ["Borrador", "Publicada", "Cerrada", "Archivada"])
    expect(screen.getByText(label)).toBeTruthy();
  const row = screen.getByText("Tarea 0").closest("tr");
  expect(within(row).getByText("02/11/2026 18:30").getAttribute("datetime")).toBe("2026-11-02T18:30:00");
  expect(within(row).getByText("20")).toBeTruthy();
  expect(calls.filter((call) => call.path.startsWith("/api/asignaciones-cursos"))).toHaveLength(1);
  expect(calls[0].query).toEqual({ page: "0", size: "20" });
  expect(calls.every((call) => call.method === "GET")).toBe(true);
  expect(screen.queryByText(/Crear|Editar|Ver detalle/)).toBeNull();
});

test("Plazo vencido es secundario solo en PUBLICADA y desaparece al cerrar", async () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(2026, 9, 30, 12, 0, 0));
  try {
    let state = "PUBLICADA";
    backend({ "GET /api/tareas": () => page([
      task({ estado: state, fechaLimite: "2026-10-30T11:59:59" }),
      task({ id: 2, nombre: "Futura", estado: "PUBLICADA", fechaLimite: "2026-10-30T12:00:01" }),
      task({ id: 3, nombre: "Borrador pasado", fechaLimite: "2026-10-29T12:00:00" }),
      task({ id: 4, nombre: "Archivada pasada", estado: "ARCHIVADA", fechaLimite: "2026-10-29T12:00:00" }),
    ]) });
    render(<Tasks canWrite />);
    const row = (await screen.findByText("Práctica de álgebra")).closest("tr");
    expect(within(row).getByText("Publicada")).toBeTruthy();
    expect(within(row).getByText("Plazo vencido").closest("td").getAttribute("data-label")).toBe("Estado");
    expect(within(row).getByRole("button", { name: "Cerrar Práctica de álgebra" }).disabled).toBe(false);
    expect(screen.getAllByText("Plazo vencido")).toHaveLength(1);
    state = "CERRADA";
    await userEvent.setup().click(screen.getByText("Actualizar"));
    await screen.findByText("Cerrada");
    expect(screen.queryByText("Plazo vencido")).toBeNull();
    expect(screen.getByRole("button", { name: "Archivar Práctica de álgebra" })).toBeTruthy();
  } finally { vi.useRealTimers(); }
});

test("Actualiza la indicación temporal al volver a enfocar la ventana", async () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(2026, 9, 30, 12, 0, 0));
  try {
    backend({ "GET /api/tareas": page([task({ estado: "PUBLICADA", fechaLimite: "2026-10-30T12:00:00" })]) });
    render(<Tasks />);
    await screen.findByText("Práctica de álgebra");
    expect(screen.queryByText("Plazo vencido")).toBeNull();
    vi.setSystemTime(new Date(2026, 9, 30, 12, 0, 1));
    await act(async () => window.dispatchEvent(new Event("focus")));
    expect(screen.getByText("Plazo vencido")).toBeTruthy();
    expect(screen.getByText("Publicada")).toBeTruthy();
  } finally { vi.useRealTimers(); }
});

test("Pagina y consulta únicamente el contexto de las tareas de cada página", async () => {
  const calls = backend({
    "GET /api/tareas": ({ query }) => ({
      content: [task(query.page === "1" ? { id: 2, nombre: "Lectura", asignacionCursoId: 43 } : {})],
      number: Number(query.page), totalPages: 2, totalElements: 21,
    }),
    "GET /api/asignaciones-cursos/43": assignment({ id: 43, curso: "Comunicación" }),
  });
  const user = userEvent.setup();
  render(<Tasks />);
  await screen.findByText("Matemática · 2° B · 2026");
  expect(screen.getByText("Anterior").disabled).toBe(true);
  await user.click(screen.getByText("Siguiente"));
  expect(await screen.findByText("Comunicación · 2° B · 2026")).toBeTruthy();
  expect(screen.queryByText("Práctica de álgebra")).toBeNull();
  expect(screen.getByText("Siguiente").disabled).toBe(true);
  expect(screen.getByText("21 registros · Página 2 de 2")).toBeTruthy();
  expect(calls.filter((call) => call.path === "/api/tareas").map((call) => call.query.page)).toEqual(["0", "1"]);
});

test("Un fallo de contexto no impide leer las tareas y Actualizar permite recuperarlo", async () => {
  let fails = true;
  const calls = backend({ "GET /api/asignaciones-cursos/42": () => fails ? reply(403) : assignment() });
  const user = userEvent.setup();
  render(<Tasks />);
  expect(await screen.findByText("Curso/sección no disponible")).toBeTruthy();
  expect(screen.getByText("Práctica de álgebra")).toBeTruthy();
  expect(screen.getByText(/No se pudo resolver/)).toBeTruthy();
  fails = false;
  await user.click(screen.getByText("Actualizar"));
  await screen.findByText("Matemática · 2° B · 2026");
  expect(screen.queryByText(/No se pudo resolver/)).toBeNull();
  expect(calls.filter((call) => call.path === "/api/tareas")).toHaveLength(2);
});

test("Sin tareas muestra vacío sin consultar asignaciones", async () => {
  const calls = backend({ "GET /api/tareas": page([]) });
  render(<Tasks />);
  expect(await screen.findByText("Aún no tienes tareas registradas.")).toBeTruthy();
  expect(calls).toHaveLength(1);
});

test("Una respuesta tardía de contexto no contamina la siguiente página", async () => {
  let resolveContext;
  const calls = backend({
    "GET /api/tareas": ({ query }) => ({ content: [task(query.page === "1"
      ? { id: 2, nombre: "Lectura", asignacionCursoId: 43 } : {})],
      number: Number(query.page), totalPages: 2, totalElements: 21 }),
    "GET /api/asignaciones-cursos/42": () => new Promise((resolve) => { resolveContext = resolve; }),
    "GET /api/asignaciones-cursos/43": assignment({ id: 43, curso: "Comunicación" }),
  });
  render(<Tasks />);
  await waitFor(() => expect(calls.some((call) => call.path.endsWith("/42"))).toBe(true));
  expect(screen.getByText("Cargando curso/sección…")).toBeTruthy();
  await userEvent.setup().click(screen.getByText("Siguiente"));
  await screen.findByText("Comunicación · 2° B · 2026");
  await act(async () => { resolveContext(assignment()); });
  expect(screen.queryByText("Matemática · 2° B · 2026")).toBeNull();
  expect(screen.getByText("Comunicación · 2° B · 2026")).toBeTruthy();
});

test("Sin permiso no inicia consultas desde ModuleOutlet", () => {
  const calls = backend();
  render(<SessionUserProvider value={{ id: "999", role: "DOCENTE", permissions: [] }}>
    <ModuleOutlet />
  </SessionUserProvider>);
  expect(screen.getByText("No tienes permiso para acceder a esta opción.")).toBeTruthy();
  expect(calls).toHaveLength(0);
});

test("Un error al cambiar de página oculta los datos anteriores y se puede reintentar", async () => {
  const calls = backend({ "GET /api/tareas": ({ query }) => query.page === "1"
    ? reply(403, { message: "Consulta no permitida" }) : page([task()], 2) });
  const user = userEvent.setup();
  render(<Tasks />);
  await screen.findByText("Práctica de álgebra");
  await user.click(screen.getByText("Siguiente"));
  expect((await screen.findByRole("alert")).textContent).toBe("Consulta no permitida");
  expect(screen.queryByRole("table")).toBeNull();
  expect(screen.queryByText("Aún no tienes tareas registradas.")).toBeNull();
  await user.click(screen.getByText("Actualizar"));
  await waitFor(() => expect(calls.filter((call) => call.path === "/api/tareas")).toHaveLength(3));
});

test("El servicio normaliza páginas Spring y consulta el detalle sin alterar el DTO", async () => {
  const value = task();
  const calls = backend({
    "GET /api/tareas": { content: [value], page: { number: 2, totalElements: 61, totalPages: 4 } },
    "GET /api/tareas/1": value,
  });
  const result = await listarTareas(2, 20, 42);
  expect(result).toEqual({ content: [value], page: 2, totalElements: 61, totalPages: 4, first: false, last: false });
  expect(calls[0].query).toEqual({ page: "2", size: "20", asignacionCursoId: "42" });
  expect(await obtenerTarea(1)).toEqual(value);
});

test("ModuleOutlet registra Tareas para docente y exige TAREAS_LEER", async () => {
  const calls = backend();
  const docente = { id: "999", role: "DOCENTE", fullName: "Ana Pérez", permissions: ["TAREAS_LEER"] };
  const { rerender } = render(<SessionUserProvider value={docente}><ModuleOutlet /></SessionUserProvider>);
  expect(await screen.findByText("Práctica de álgebra")).toBeTruthy();
  expect(canAccess(docente, "Tareas")).toBe(true);
  rerender(<SessionUserProvider value={{ ...docente, permissions: [] }}><ModuleOutlet /></SessionUserProvider>);
  expect(screen.getByText("No tienes permiso para acceder a esta opción.")).toBeTruthy();
  expect(screen.queryByRole("table")).toBeNull();
  expect(canAccess({ ...docente, permissions: [] }, "Tareas")).toBe(false);
  expect(canAccess({ ...docente, role: "ADMINISTRADOR" }, "Tareas")).toBe(false);
  expect(calls.filter((call) => call.path === "/api/tareas")).toHaveLength(1);
});
