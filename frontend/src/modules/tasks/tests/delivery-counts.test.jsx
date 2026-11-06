import { beforeEach, expect, test } from "vitest";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { Tasks } from "@/modules/tasks";

const task = (id, estado = "PUBLICADA") => ({
  id, asignacionCursoId: 42, nombre: `Tarea ${id}`, estado, version: 1,
  fechaLimite: "2099-12-01T18:30:00", puntajeMaximo: 20,
});
const summary = (tareaId, entregados = 3, totalAlumnos = 30) => ({
  tareaId, entregados, totalAlumnos, calificados: 0, pendientes: totalAlumnos - entregados, alumnos: [],
});
const row = (id) => screen.getByText(`Tarea ${id}`).closest("tr");
const cell = (id) => row(id).querySelector('[data-label="Entregas"]');
const countsCalls = (calls) => calls.filter((call) => call.path.endsWith("/seguimiento"));
function backend(routes = {}) {
  return fakeBackend({
    "GET /api/tareas": page([task(1)]),
    "GET /api/asignaciones-cursos/42": { id: 42, curso: "Matemática", seccion: "A", anioAcademico: 2026 },
    ...routes,
  });
}
beforeEach(() => signIn({ role: "DOCENTE", permissions: ["TAREAS_LEER"] }));

test("Entregas precede a Acciones y usa contadores reales, ceros y fallback sin inventar totales", async () => {
  let release;
  const calls = backend({
    "GET /api/tareas": page([task(1), task(2, "CERRADA"), task(3, "ARCHIVADA"), task(4, "BORRADOR")]),
    "GET /api/tareas/1/seguimiento": () => new Promise((resolve) => { release = resolve; }),
    "GET /api/tareas/2/seguimiento": summary(2, 0, 0),
    "GET /api/tareas/3/seguimiento": reply(403),
  });
  render(<Tasks />);
  const headings = await screen.findAllByRole("columnheader");
  expect(headings.slice(-2).map((heading) => heading.textContent)).toEqual(["Entregas", "Acciones"]);
  expect(within(cell(1)).getByText("—")).toBeTruthy();
  expect(within(cell(1)).getByText("Resumen de entregas no disponible")).toBeTruthy();
  await within(cell(2)).findByText("0 de 0");
  await waitFor(() => expect(release).toBeTypeOf("function"));
  await act(async () => { release(summary(1)); });
  expect(await within(cell(1)).findByText("3 de 30")).toBeTruthy();
  expect(within(cell(1)).getByText("estudiantes entregaron")).toBeTruthy();
  expect(within(cell(3)).getByText("—")).toBeTruthy();
  expect(within(cell(4)).getByText("—")).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();
  expect(countsCalls(calls)).toHaveLength(3);
  expect(calls.some((call) => /\/entregas$|\/alumnos/.test(call.path))).toBe(false);
});

test("Consulta solo la página visible, reutiliza al regresar y renueva al actualizar", async () => {
  let delivered = 3;
  const calls = backend({
    "GET /api/tareas": ({ query }) => ({
      content: [task(Number(query.page) + 1)], number: Number(query.page), totalPages: 2, totalElements: 21,
    }),
    "GET /api/tareas/:id/seguimiento": ({ path }) => summary(Number(path.split("/")[3]), delivered),
  });
  render(<Tasks />);
  const user = userEvent.setup();
  await screen.findByText("3 de 30");
  expect(countsCalls(calls).map((call) => call.path)).toEqual(["/api/tareas/1/seguimiento"]);
  await user.click(screen.getByText("Siguiente"));
  await screen.findByText("Tarea 2");
  await within(cell(2)).findByText("3 de 30");
  await user.click(screen.getByText("Anterior"));
  await screen.findByText("Tarea 1");
  await within(cell(1)).findByText("3 de 30");
  await act(async () => { window.dispatchEvent(new Event("focus")); });
  expect(countsCalls(calls)).toHaveLength(2);
  delivered = 4;
  await user.click(screen.getByText("Actualizar"));
  await screen.findByText("4 de 30");
  expect(countsCalls(calls).map((call) => call.path)).toEqual([
    "/api/tareas/1/seguimiento", "/api/tareas/2/seguimiento", "/api/tareas/1/seguimiento",
  ]);
});

test("Un resumen fallido o de otra tarea usa fallback y se reintenta al actualizar", async () => {
  let valid = false;
  const calls = backend({ "GET /api/tareas/1/seguimiento": () => summary(valid ? 1 : 99) });
  render(<Tasks />);
  await screen.findByText("Tarea 1");
  await waitFor(() => expect(countsCalls(calls)).toHaveLength(1));
  expect(within(cell(1)).getByText("—")).toBeTruthy();
  valid = true;
  await userEvent.setup().click(screen.getByText("Actualizar"));
  expect(await screen.findByText("3 de 30")).toBeTruthy();
});

test("Limita las consultas simultáneas y deja de cargar filas al salir del listado", async () => {
  const releases = [];
  const calls = backend({
    "GET /api/tareas": page([1, 2, 3, 4, 5].map((id) => task(id))),
    "GET /api/tareas/:id/seguimiento": ({ path }) => new Promise((resolve) => {
      releases.push(() => resolve(summary(Number(path.split("/")[3]))));
    }),
  });
  const view = render(<Tasks />);
  await waitFor(() => expect(releases).toHaveLength(3));
  expect(countsCalls(calls)).toHaveLength(3);
  view.unmount();
  await act(async () => { releases.forEach((release) => release()); });
  expect(countsCalls(calls)).toHaveLength(3);
});
