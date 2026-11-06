import { beforeEach, expect, test, vi } from "vitest";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn, signOut } from "../../../../tests/support/backend.js";
import { Tasks } from "@/modules/tasks";
import { TaskTransitionDialog } from "../task-transition-dialog";

const task = (estado = "BORRADOR") => ({ id: 1, asignacionCursoId: 42, nombre: "Práctica", descripcion: "Resolver", fechaPublicacion: null, fechaLimite: "2020-01-01T12:00:00", puntajeMaximo: 20, maxArchivosEntrega: 0, estado, archivos: [], version: 3 });
beforeEach(() => signIn({ role: "DOCENTE", permissions: ["TAREAS_LEER", "TAREAS_ESCRIBIR"] }));

test.each([
  ["BORRADOR", "publicar", "Publicar", "PUBLICADA", "Tarea publicada."],
  ["PUBLICADA", "cerrar", "Cerrar", "CERRADA", "Tarea cerrada."],
  ["CERRADA", "archivar", "Archivar", "ARCHIVADA", "Tarea archivada."],
])("Transición desde %s: confirma, envía POST vacío y refresca", async (initial, action, label, next, feedback) => {
  let current = task(initial);
  const calls = fakeBackend({
    "GET /api/tareas": () => page([current]),
    "GET /api/asignaciones-cursos/42": { id: 42, curso: "Matemática", seccion: "A", anioAcademico: 2026 },
    [`POST /api/tareas/1/${action}`]: () => { current = task(next); return current; },
  });
  const user = userEvent.setup();
  render(<Tasks canWrite />);
  await user.click(await screen.findByRole("button", { name: `${label} Práctica` }));
  const dialog = screen.getByRole("dialog", { name: `${label} tarea` });
  expect(calls.some((call) => call.method === "POST")).toBe(false);
  await user.click(within(dialog).getByRole("button", { name: `${label} tarea` }));
  const notice = await screen.findByText(feedback);
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(notice.closest("section")).toBeNull();
  expect(notice.parentElement.nextElementSibling.getAttribute("aria-label")).toBe("Listado de tareas");
  const request = calls.find((call) => call.method === "POST");
  expect(request.path).toBe(`/api/tareas/1/${action}`);
  expect(request.body).toBeUndefined();
  await waitFor(() => expect(calls.filter((call) => call.path === "/api/tareas")).toHaveLength(2));
  await user.click(screen.getByRole("button", { name: "Cerrar mensaje de éxito" }));
  expect(screen.queryByText(feedback)).toBeNull();
});

test("Acciones exactas por estado, y ninguna sin permiso de escritura", async () => {
  fakeBackend({ "GET /api/tareas": page(["BORRADOR", "PUBLICADA", "CERRADA", "ARCHIVADA"].map((estado, id) => ({ ...task(estado), id, nombre: estado }))) });
  const { rerender } = render(<Tasks canWrite />);
  await screen.findByRole("button", { name: "Publicar BORRADOR" });
  expect(screen.getByRole("button", { name: "Editar BORRADOR" })).toBeTruthy();
  expect(screen.getByRole("button", { name: "Editar PUBLICADA" })).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Editar CERRADA" })).toBeNull();
  expect(screen.getByRole("button", { name: "Cerrar PUBLICADA" })).toBeTruthy();
  expect(screen.getByRole("button", { name: "Archivar CERRADA" })).toBeTruthy();
  // US-22: se puede eliminar en cualquier estado.
  expect(within(screen.getByText("ARCHIVADA").closest("tr")).getAllByRole("button").map((b) => b.getAttribute("aria-label")))
    .toEqual(["Eliminar ARCHIVADA"]);
  expect(screen.getAllByRole("button", { name: /^Eliminar / })).toHaveLength(4);
  rerender(<Tasks />);
  expect(screen.queryByRole("button", { name: /Publicar|Archivar|Editar|Eliminar|Nueva tarea|Cerrar PUBLICADA/ })).toBeNull();
});

test.each([
  [422, "TAREA_TRANSICION_INVALIDA", /El estado de la tarea cambió/, true],
  [422, "TAREA_FECHA_LIMITE_VENCIDA", /la fecha límite ya se alcanzó/, false],
  [422, "TAREA_PUBLICACION_FUTURA", /la fecha de publicación está en el futuro/, false],
  [422, "TAREA_AUN_VIGENTE", /todavía está vigente/, false],
  [401, "AUTENTICACION_REQUERIDA", /La sesión no es válida/, true],
  [403, "ACCESO_DENEGADO", /No tienes permiso/, true],
  [404, "TAREA_NO_ENCONTRADA", /ya no está disponible/, true],
  [409, "VERSION_DESACTUALIZADA", /modificada por otro usuario/, true],
  [500, "ERROR_INTERNO", /Error de prueba/, false],
])("Error %s %s queda en la confirmación y no repite POST", async (status, code, expected, disabled) => {
  if (status === 401) signOut(); // Prueba el 401 definitivo, sin renovación pendiente.
  const calls = fakeBackend({ "POST /api/tareas/1/publicar": reply(status, { code, message: "Error de prueba" }) });
  const onSaved = vi.fn(), onRefresh = vi.fn();
  render(<TaskTransitionDialog task={task()} action="publicar" onClose={vi.fn()} onSaved={onSaved} onRefresh={onRefresh} />);
  await userEvent.setup().click(screen.getByRole("button", { name: "Publicar tarea" }));
  expect((await screen.findByRole("alert")).textContent).toMatch(expected);
  expect(screen.getByRole("dialog")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Publicar tarea" }).disabled).toBe(disabled);
  expect(calls).toHaveLength(1);
  expect(onSaved).not.toHaveBeenCalled();
  await userEvent.setup().click(screen.getByText("Actualizar listado"));
  expect(onRefresh).toHaveBeenCalledOnce();
});

test("Bloquea doble envío y cierre mientras procesa; Cancelar no envía", async () => {
  let release;
  const calls = fakeBackend({ "POST /api/tareas/1/cerrar": () => new Promise((resolve) => { release = resolve; }) });
  const onClose = vi.fn(), onSaved = vi.fn();
  render(<TaskTransitionDialog task={task("PUBLICADA")} action="cerrar" onClose={onClose} onRefresh={vi.fn()} onSaved={onSaved} />);
  const user = userEvent.setup();
  expect(screen.getByText(/según su reloj/)).toBeTruthy();
  await user.click(screen.getByText("Cancelar"));
  expect(onClose).toHaveBeenCalledOnce();
  expect(calls).toHaveLength(0);
  await user.dblClick(screen.getByRole("button", { name: "Cerrar tarea" }));
  expect(screen.getByText("Procesando…").disabled).toBe(true);
  expect(screen.getByText("Cancelar").disabled).toBe(true);
  await user.keyboard("{Escape}");
  expect(onClose).toHaveBeenCalledOnce();
  expect(calls).toHaveLength(1);
  await act(async () => { release(task("CERRADA")); });
  expect(onSaved).toHaveBeenCalledOnce();
});

test("El éxito se limpia al iniciar otra operación", async () => {
  fakeBackend({
    "GET /api/tareas": page([task("CERRADA")]),
    "POST /api/tareas/1/archivar": task("ARCHIVADA"),
  });
  render(<Tasks canWrite />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "Archivar Práctica" }));
  await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Archivar tarea" }));
  await screen.findByText("Tarea archivada.");
  await waitFor(() => expect(screen.getByText("Actualizar").disabled).toBe(false));
  await user.click(screen.getByText("Actualizar"));
  expect(screen.queryByText("Tarea archivada.")).toBeNull();
});
