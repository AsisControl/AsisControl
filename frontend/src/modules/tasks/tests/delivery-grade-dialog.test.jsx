// cobertura: calificar o devolver una entrega
import { beforeEach, expect, test, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, reply, signIn } from "../../../../tests/support/backend.js";
import { DeliveryGradeDialog } from "../delivery-grade-dialog";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const entrega = {
  id: 3, tareaId: 8, estado: "ENTREGADO", nota: null, nivelLogro: null, comentario: null, version: 4,
  fechaHoraEntrega: "2026-11-18T10:30:00", respuesta: "Mi respuesta", archivos: [],
};
const tarea = { id: 8, nombre: "Ensayo", estado: "PUBLICADA", puntajeMaximo: 20, modeloCualitativo: false };

function abrir({ delivery = entrega, task = tarea, routes = {} } = {}) {
  const calls = fakeBackend({ "GET /api/entregas/3": delivery, "GET /api/tareas/8": task, ...routes });
  const props = { onClose: vi.fn(), onRefresh: vi.fn(), onSaved: vi.fn() };
  render(<DeliveryGradeDialog deliveryId={3} taskId={8} student="Lucía Prado" {...props} />);
  return { calls, ...props };
}

beforeEach(() => signIn({ role: "DOCENTE", permissions: ["TAREAS_CALIFICAR"] }));

test("valida la nota y califica con comentario", async () => {
  const { calls, onSaved } = abrir({ routes: { "POST /api/entregas/3/calificar": { ...entrega, nota: 18 } } });
  expect(await screen.findByText("Mi respuesta")).toBeTruthy();
  const nota = screen.getByLabelText(/^Nota/);
  await userEvent.type(nota, "25");
  await userEvent.click(screen.getByRole("button", { name: "Guardar calificación" }));
  expect(await screen.findByText("Escribe una nota válida entre 0 y 20.")).toBeTruthy();
  await userEvent.clear(nota);
  await userEvent.type(nota, "18");
  await userEvent.type(screen.getByLabelText("Comentario"), "  Buen trabajo  ");
  await userEvent.click(screen.getByRole("button", { name: "Guardar calificación" }));
  expect(onSaved).toHaveBeenCalledWith(false);
  expect(calls.find(c => c.method === "POST").body).toEqual({ nota: 18, comentario: "Buen trabajo", version: 4 });
});

test("en el modelo cualitativo guarda solo la devolución si no se elige nivel, o califica con nivel", async () => {
  const task = { ...tarea, modeloCualitativo: true };
  const { calls, onSaved } = abrir({ task, routes: {
    "PUT /api/entregas/3/devolucion": entrega, "POST /api/entregas/3/calificar": entrega,
  } });
  await screen.findByText("Mi respuesta");
  await userEvent.type(screen.getByLabelText("Comentario"), "Revisa la conclusión");
  await userEvent.click(screen.getByRole("button", { name: "Guardar devolución" }));
  expect(onSaved).toHaveBeenCalledWith(true);
  expect(calls.find(c => c.method === "PUT").body).toEqual({ comentario: "Revisa la conclusión", version: 4 });
  await userEvent.click(screen.getByRole("button", { name: "A: Nivel de la práctica" }));
  await userEvent.click(screen.getByRole("button", { name: "Guardar calificación" }));
  expect(calls.find(c => c.method === "POST").body).toEqual({ nivelLogro: "A", comentario: "Revisa la conclusión", version: 4 });
});

test("si la entrega cambió pide descartar y actualizar", async () => {
  abrir({ delivery: { ...entrega, nota: 12, comentario: "Antes" },
    routes: { "POST /api/entregas/3/calificar": reply(409, { code: "VERSION_DESACTUALIZADA", message: "x" }) } });
  expect(await screen.findByRole("heading", { name: "Modificar calificación" })).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "Guardar calificación" }));
  expect(await screen.findByText("La entrega cambió. Actualiza sus datos antes de guardar.")).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "Descartar cambios y actualizar" }));
  expect(await screen.findByLabelText(/^Nota/)).toBeTruthy();
});

test("si la tarea ya no es calificable bloquea el formulario y ofrece actualizar el seguimiento", async () => {
  const { onRefresh, onClose } = abrir({
    routes: { "POST /api/entregas/3/calificar": reply(422, { code: "TAREA_NO_CALIFICABLE", message: "x" }) } });
  await userEvent.type(await screen.findByLabelText(/^Nota/), "15");
  await userEvent.click(screen.getByRole("button", { name: "Guardar calificación" }));
  expect(await screen.findByText("La tarea ya no permite calificar. Actualiza el seguimiento.")).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "Actualizar seguimiento" }));
  expect(onRefresh).toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "Cerrar" }));
  expect(onClose).toHaveBeenCalled();
});

test("una entrega anulada o de una tarea en borrador no se califica", async () => {
  abrir({ delivery: { ...entrega, estado: "ANULADO" } });
  expect(await screen.findByText("Solo se pueden calificar entregas no anuladas de tareas publicadas o cerradas.")).toBeTruthy();
});

test("si falla la carga o la entrega es de otra tarea permite reintentar", async () => {
  let ok = false;
  fakeBackend({
    "GET /api/entregas/3": () => (ok ? entrega : { ...entrega, tareaId: 99 }),
    "GET /api/tareas/8": tarea,
  });
  render(<DeliveryGradeDialog deliveryId={3} taskId={8} student="Lucía Prado" onClose={vi.fn()} onRefresh={vi.fn()} onSaved={vi.fn()} />);
  expect(await screen.findByText("La entrega no pertenece a esta tarea.")).toBeTruthy();
  ok = true;
  await userEvent.click(screen.getByRole("button", { name: "Reintentar carga" }));
  expect(await screen.findByText("Mi respuesta")).toBeTruthy();
});

test("al cerrar con cambios pide confirmar antes de descartarlos", async () => {
  const { onClose } = abrir();
  await userEvent.type(await screen.findByLabelText(/^Nota/), "10");
  await userEvent.keyboard("{Escape}");
  expect(await screen.findByRole("alertdialog", { name: "¿Descartar cambios de la entrega?" })).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "Seguir editando" }));
  expect(onClose).not.toHaveBeenCalled();
  await userEvent.keyboard("{Escape}");
  await userEvent.click(await screen.findByRole("button", { name: "Descartar cambios" }));
  expect(onClose).toHaveBeenCalled();
});
