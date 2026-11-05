import { beforeEach, expect, test } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { Tasks } from "@/modules/tasks";

const task = (estado, overrides = {}) => ({ id: 1, asignacionCursoId: 42, nombre: "Práctica", descripcion: "Resolver", fechaPublicacion: null, fechaLimite: "2099-01-01T12:00:00", puntajeMaximo: 20, maxArchivosEntrega: 0, estado, archivos: [], version: 3, ...overrides });
beforeEach(() => signIn({ role: "DOCENTE", permissions: ["TAREAS_LEER", "TAREAS_ESCRIBIR"] }));

function backend(estado, routes = {}) {
  let deleted = false;
  const calls = fakeBackend({
    "GET /api/tareas": () => page(deleted ? [] : [task(estado)]),
    "GET /api/asignaciones-cursos/42": { id: 42, curso: "Matemática", seccion: "A", anioAcademico: 2026, activo: true },
    "DELETE /api/tareas/1": () => { deleted = true; return reply(204); },
    ...routes,
  });
  return calls;
}

async function openDelete() {
  const user = userEvent.setup();
  render(<Tasks canWrite />);
  await user.click(await screen.findByRole("button", { name: "Eliminar Práctica" }));
  return { user, dialog: screen.getByRole("dialog", { name: "Eliminar tarea" }) };
}

test("Un borrador se elimina sin motivo y sin consultar entregas", async () => {
  const calls = backend("BORRADOR");
  const { user, dialog } = await openDelete();
  expect(within(dialog).queryByLabelText(/^Motivo/)).toBeNull();
  await user.click(within(dialog).getByRole("button", { name: "Eliminar tarea" }));
  expect(await screen.findByText("Tarea eliminada.")).toBeTruthy();
  expect(calls.some((call) => call.path.endsWith("/entregas"))).toBe(false);
  expect(calls.find((call) => call.method === "DELETE").body).toEqual({ motivo: null });
  expect(await screen.findByText("Aún no tienes tareas registradas.")).toBeTruthy();
});

test("Con entregas exige motivo y lo envía recortado", async () => {
  const calls = backend("CERRADA", { "GET /api/tareas/1/entregas": page([{ id: 10 }, { id: 11 }]) });
  const { user, dialog } = await openDelete();
  expect(await within(dialog).findByText(/La tarea tiene 2 entregas/)).toBeTruthy();
  await user.click(within(dialog).getByRole("button", { name: "Eliminar tarea" }));
  expect(await within(dialog).findByText(/indica el motivo de la eliminación/)).toBeTruthy();
  expect(calls.some((call) => call.method === "DELETE")).toBe(false);
  await user.type(within(dialog).getByLabelText(/^Motivo/), "  Duplicada  ");
  await user.click(within(dialog).getByRole("button", { name: "Eliminar tarea" }));
  await screen.findByText("Tarea eliminada.");
  expect(calls.find((call) => call.method === "DELETE").body).toEqual({ motivo: "Duplicada" });
});

test("Publicada sin entregas se elimina sin motivo; el rechazo del backend se muestra", async () => {
  const calls = backend("PUBLICADA", {
    "GET /api/tareas/1/entregas": page([]),
    "DELETE /api/tareas/1": reply(422, { code: "TAREA_MOTIVO_REQUERIDO", message: "Indique el motivo de la eliminación",
      fieldErrors: [{ field: "motivo", message: "Indique el motivo de la eliminación" }] }),
  });
  const { user, dialog } = await openDelete();
  await waitFor(() => expect(within(dialog).getByText(/dejará de mostrarse/)).toBeTruthy());
  expect(within(dialog).queryByLabelText(/^Motivo/)).toBeNull();
  await user.click(within(dialog).getByRole("button", { name: "Eliminar tarea" }));
  expect((await within(dialog).findAllByText("Indique el motivo de la eliminación")).length).toBeGreaterThan(0);
  expect(calls.filter((call) => call.method === "DELETE")).toHaveLength(1);
});

test("Con la asignación retirada no se ofrece eliminar", async () => {
  backend("BORRADOR", { "GET /api/asignaciones-cursos/42": { id: 42, curso: "Matemática", seccion: "A", anioAcademico: 2026, activo: false } });
  render(<Tasks canWrite />);
  await screen.findByText("Asignación retirada: solo consulta");
  expect(screen.queryByRole("button", { name: "Eliminar Práctica" })).toBeNull();
});
