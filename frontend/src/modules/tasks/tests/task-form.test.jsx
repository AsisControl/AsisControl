import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { Tasks } from "@/modules/tasks";
import { TaskFormDialog } from "../task-form-dialog";
import { fileError, validateTask } from "../task-form-rules";
import { TaskAttachments } from "../task-attachments";

const attachment = (id = 9) => ({ id, nombreOriginal: `guia-${id}.pdf`, tipoMime: "application/pdf", tamanioBytes: 40, sha256: "hash", cargadoPorPersonaId: 77, fechaSubida: "2026-10-29T12:00:00Z" });
const task = (overrides = {}) => ({ id: 1, asignacionCursoId: 42, nombre: "Álgebra", descripcion: "Resolver ejercicios", fechaPublicacion: null, fechaLimite: "2099-12-01T18:30:12", puntajeMaximo: 20, maxArchivosEntrega: 2, estado: "BORRADOR", archivos: [attachment()], version: 7, ...overrides });
function backend(routes = {}) {
  return fakeBackend({
    "GET /api/tareas": page([task(), task({ id: 2, nombre: "Publicada", estado: "PUBLICADA" })]),
    "GET /api/tareas/1": task(),
    "GET /api/asignaciones-cursos/42": { id: 42, curso: "Matemática", seccion: "A", anioAcademico: 2026 },
    "PUT /api/tareas/1": ({ body }) => task({ ...body, version: 8 }),
    ...routes,
  });
}
beforeEach(() => signIn({ role: "DOCENTE", permissions: ["TAREAS_LEER", "TAREAS_ESCRIBIR", "ARCHIVOS_ESCRIBIR"] }));

test("Edita un borrador, conserva versión y precisión de fechas, y refresca el listado", async () => {
  const calls = backend();
  render(<Tasks canWrite canUpload />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "Editar Álgebra" }));
  const dialog = await screen.findByRole("dialog", { name: "Editar tarea" });
  const name = await within(dialog).findByLabelText(/^Nombre/);
  expect(screen.getByRole("button", { name: "Editar Publicada" })).toBeTruthy();
  expect(within(dialog).queryByRole("combobox")).toBeNull();
  expect(within(dialog).queryByLabelText(/^Motivo/)).toBeNull();
  await user.clear(name);
  await user.type(name, " Nueva álgebra ");
  await user.click(within(dialog).getByText("Guardar cambios"));
  expect(await screen.findByText("Borrador actualizado.")).toBeTruthy();
  expect(calls.find((call) => call.method === "PUT").body).toEqual({ nombre: "Nueva álgebra", descripcion: "Resolver ejercicios", fechaPublicacion: null, fechaLimite: "2099-12-01T18:30:12", puntajeMaximo: 20, maxArchivosEntrega: 2, archivoIds: [9], version: 7, motivo: null });
  await waitFor(() => expect(calls.filter((call) => call.path === "/api/tareas")).toHaveLength(2));
});

const published = (overrides = {}) => task({ estado: "PUBLICADA", fechaPublicacion: "2026-10-01T08:00:00.123456", ...overrides });

test("Publicada con entregas: conserva la publicación y exige motivo al cambiar la fecha límite", async () => {
  const calls = backend({
    "GET /api/tareas/1": published(),
    "GET /api/tareas/1/entregas": page([{ id: 10 }]),
    "PUT /api/tareas/1": ({ body }) => published({ ...body, version: 8 }),
  });
  const onSaved = vi.fn();
  render(<TaskFormDialog taskId={1} canUpload onClose={vi.fn()} onSaved={onSaved} />);
  const user = userEvent.setup();
  const { fireEvent } = await import("@testing-library/react");
  await screen.findByLabelText(/^Nombre/);
  const dialog = screen.getByRole("dialog", { name: "Editar tarea" });
  expect(within(dialog).getByText(/seguirá publicada/)).toBeTruthy();
  expect(within(dialog).getByLabelText(/^Fecha de publicación/).readOnly).toBe(true);
  expect(await within(dialog).findByText(/tiene 1 entrega: cambiar esta fecha exige un motivo/)).toBeTruthy();
  expect(calls.find((call) => call.path === "/api/tareas/1/entregas").query).toEqual({ page: "0", size: "1" });

  fireEvent.change(within(dialog).getByLabelText(/^Fecha límite/), { target: { value: "2099-12-05T18:30" } });
  await user.click(within(dialog).getByText("Guardar cambios"));
  expect(await within(dialog).findByText(/indica el motivo del cambio de fecha límite/)).toBeTruthy();
  expect(calls.some((call) => call.method === "PUT")).toBe(false);

  await user.type(within(dialog).getByLabelText(/^Motivo del cambio/), " Feriado ");
  await user.click(within(dialog).getByText("Guardar cambios"));
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
  const body = calls.find((call) => call.method === "PUT").body;
  expect(body.fechaPublicacion).toBe("2026-10-01T08:00:00.123456");
  expect(body.fechaLimite).toBe("2099-12-05T18:30");
  expect(body.motivo).toBe("Feriado");
});

test("Publicada sin entregas: el motivo es opcional y no se envía si no cambia el vencimiento", async () => {
  const calls = backend({
    "GET /api/tareas/1": published(),
    "GET /api/tareas/1/entregas": page([]),
    "PUT /api/tareas/1": ({ body }) => published({ ...body, version: 8 }),
  });
  render(<TaskFormDialog taskId={1} canUpload onClose={vi.fn()} onSaved={vi.fn()} />);
  const user = userEvent.setup();
  const { fireEvent } = await import("@testing-library/react");
  await screen.findByLabelText(/^Nombre/);
  const dialog = screen.getByRole("dialog", { name: "Editar tarea" });
  await waitFor(() => expect(calls.some((call) => call.path === "/api/tareas/1/entregas")).toBe(true));
  fireEvent.change(within(dialog).getByLabelText(/^Fecha límite/), { target: { value: "2099-12-05T18:30" } });
  expect(await within(dialog).findByText(/Opcional: aún no hay entregas/)).toBeTruthy();
  fireEvent.change(within(dialog).getByLabelText(/^Fecha límite/), { target: { value: "2099-12-01T18:30:12" } });
  expect(within(dialog).queryByLabelText(/^Motivo del cambio/)).toBeNull();
  await user.click(within(dialog).getByText("Guardar cambios"));
  await waitFor(() => expect(calls.some((call) => call.method === "PUT")).toBe(true));
  expect(calls.find((call) => call.method === "PUT").body.motivo).toBeNull();
});

test("Si falla el conteo exige motivo localmente y conserva los errores de campo del backend", async () => {
  const calls = backend({
    "GET /api/tareas/1": published(),
    "GET /api/tareas/1/entregas": reply(500),
    "PUT /api/tareas/1": reply(422, { code: "TAREA_MOTIVO_REQUERIDO", message: "Indique el motivo",
      fieldErrors: [{ field: "motivo", message: "Indique el motivo" }] }),
  });
  render(<TaskFormDialog taskId={1} canUpload onClose={vi.fn()} onSaved={vi.fn()} />);
  const { fireEvent } = await import("@testing-library/react");
  await screen.findByLabelText(/^Nombre/);
  const dialog = screen.getByRole("dialog", { name: "Editar tarea" });
  fireEvent.change(within(dialog).getByLabelText(/^Fecha límite/), { target: { value: "2099-12-05T18:30" } });
  await userEvent.setup().click(within(dialog).getByText("Guardar cambios"));
  expect(await within(dialog).findByText(/No se pudo confirmar si hay entregas: indica el motivo/)).toBeTruthy();
  expect(calls.some((call) => call.method === "PUT")).toBe(false);
  await userEvent.setup().type(within(dialog).getByLabelText(/^Motivo del cambio/), "Ampliación del plazo");
  await userEvent.setup().click(within(dialog).getByText("Guardar cambios"));
  expect((await within(dialog).findAllByText("Indique el motivo")).length).toBeGreaterThan(0);
});

test("Nueva tarea incluye asignaciones de todas las páginas y permite elegir la última", async () => {
  const calls = backend({
    "GET /api/asignaciones-cursos/mias": ({ query }) => ({
      content: [{ id: query.page === "1" ? 43 : 42, curso: query.page === "1" ? "Física" : "Matemática", seccion: "A", anioAcademico: 2026 }],
      page: { number: Number(query.page), totalElements: 2, totalPages: 2 },
    }),
    "POST /api/tareas": ({ body }) => task({ ...body, id: 5 }),
  });
  const onSaved = vi.fn();
  render(<TaskFormDialog canUpload={false} onClose={vi.fn()} onSaved={onSaved} />);
  const user = userEvent.setup();
  await screen.findByRole("option", { name: "Física · A · 2026" });
  expect(screen.getByRole("option", { name: "Matemática · A · 2026" })).toBeTruthy();
  expect(calls.filter((call) => call.path.endsWith("/mias")).map((call) => call.query))
    .toEqual([{ size: "100", page: "0" }, { size: "100", page: "1" }]);
  await user.selectOptions(screen.getByLabelText(/^Curso y sección/), "43");
  await user.type(screen.getByLabelText(/^Nombre/), "Laboratorio");
  await user.type(screen.getByLabelText(/^Descripción/), "Informe");
  const { fireEvent } = await import("@testing-library/react");
  fireEvent.change(screen.getByLabelText(/^Fecha límite/), { target: { value: "2099-12-01T18:30" } });
  await user.click(screen.getByText("Guardar borrador"));
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
  expect(calls.find((call) => call.method === "POST").body.asignacionCursoId).toBe(43);
});

test("Nueva tarea ofrece solo las asignaciones propias y exige elegir una", async () => {
  const mine = [{ id: 42, curso: "Matemática", seccion: "A", anioAcademico: 2026 }, { id: 43, curso: "Física", seccion: "B", anioAcademico: 2026 }];
  const calls = backend({
    "GET /api/asignaciones-cursos/mias": page(mine),
    "POST /api/tareas": ({ body }) => task({ ...body, id: 5 }),
  });
  render(<Tasks canWrite canUpload />);
  const user = userEvent.setup();
  const { fireEvent } = await import("@testing-library/react");
  await user.click(await screen.findByRole("button", { name: "Nueva tarea" }));
  const dialog = await screen.findByRole("dialog", { name: "Nueva tarea" });
  const select = within(dialog).getByLabelText(/^Curso y sección/);
  await within(dialog).findByRole("option", { name: "Física · B · 2026" });
  expect(calls.some((call) => call.path === "/api/asignaciones-cursos")).toBe(false);
  await user.type(within(dialog).getByLabelText(/^Nombre/), "Laboratorio");
  await user.type(within(dialog).getByLabelText(/^Descripción/), "Informe");
  fireEvent.change(within(dialog).getByLabelText(/^Fecha límite/), { target: { value: "2099-12-01T18:30" } });
  await user.click(within(dialog).getByText("Guardar borrador"));
  expect(await within(dialog).findByText("Elige el curso y la sección de la tarea.")).toBeTruthy();
  await user.selectOptions(select, "43");
  await user.click(within(dialog).getByText("Guardar borrador"));
  expect(await screen.findByText("Borrador creado.")).toBeTruthy();
  expect(calls.find((call) => call.method === "POST").body.asignacionCursoId).toBe(43);
});

test("Nueva tarea sin asignaciones activas lo indica y preselecciona si hay una sola", async () => {
  backend({ "GET /api/asignaciones-cursos/mias": page([]) });
  const view = render(<TaskFormDialog canUpload={false} onClose={vi.fn()} onSaved={vi.fn()} />);
  expect(await screen.findByText(/No tienes cursos disponibles para crear tareas/)).toBeTruthy();
  view.unmount();
  backend({ "GET /api/asignaciones-cursos/mias": page([{ id: 42, curso: "Matemática", seccion: "A", anioAcademico: 2026 }]) });
  render(<TaskFormDialog canUpload={false} onClose={vi.fn()} onSaved={vi.fn()} />);
  await waitFor(() => expect(screen.getByLabelText(/^Curso y sección/).value).toBe("42"));
});

test("Valida obligatorios, enteros y fechas antes de enviar", async () => {
  const calls = backend();
  render(<TaskFormDialog taskId={1} canUpload onClose={vi.fn()} onSaved={vi.fn()} />);
  const user = userEvent.setup();
  await user.clear(await screen.findByLabelText(/^Nombre/));
  await user.clear(screen.getByLabelText(/^Descripción/));
  await user.clear(screen.getByLabelText(/^Puntaje máximo/));
  await user.type(screen.getByLabelText(/^Puntaje máximo/), "0");
  await user.clear(screen.getByLabelText(/^Fecha límite/));
  await user.click(screen.getByText("Guardar cambios"));
  expect(await screen.findByText(/Escribe un nombre/)).toBeTruthy();
  expect(screen.getByText(/Escribe una descripción/)).toBeTruthy();
  expect(screen.getByText(/entre 1 y 1000/)).toBeTruthy();
  expect(screen.getByText(/fecha límite válida/)).toBeTruthy();
  expect(calls.some((call) => call.method === "PUT")).toBe(false);
});

test("Conflicto de versión exige recarga explícita y usa la versión actual", async () => {
  let current = task();
  let saves = 0;
  const onSaved = vi.fn();
  const calls = backend({
    "GET /api/tareas/1": () => current,
    "PUT /api/tareas/1": () => ++saves === 1 ? reply(409, { code: "VERSION_DESACTUALIZADA", message: "Otro usuario modificó la tarea" }) : current,
  });
  render(<TaskFormDialog taskId={1} canUpload onClose={vi.fn()} onSaved={onSaved} />);
  const user = userEvent.setup();
  await screen.findByLabelText(/^Nombre/);
  await user.click(screen.getByText("Guardar cambios"));
  expect(await screen.findByText(/Tus cambios no se han guardado/)).toBeTruthy();
  expect(screen.queryByText("Guardar cambios")).toBeNull();
  expect(saves).toBe(1);
  current = task({ nombre: "Versión del servidor", version: 10 });
  await user.click(screen.getByText("Descartar cambios y actualizar"));
  expect((await screen.findByLabelText(/^Nombre/)).value).toBe("Versión del servidor");
  await user.click(screen.getByText("Guardar cambios"));
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
  expect(calls.filter((call) => call.method === "PUT").at(-1).body.version).toBe(10);
});

test("Después de recargar una tarea cerrada no permite guardarla", async () => {
  backend({ "GET /api/tareas/1": task({ estado: "CERRADA" }) });
  render(<TaskFormDialog taskId={1} canUpload onClose={vi.fn()} onSaved={vi.fn()} />);
  expect(await screen.findByText(/Solo se pueden editar borradores o tareas publicadas/)).toBeTruthy();
  expect(screen.queryByText("Guardar cambios")).toBeNull();
});

test("Un fallo al cargar permite reintentar; los errores por campo se presentan", async () => {
  let fail = true;
  backend({
    "GET /api/tareas/1": () => fail ? reply(404, { message: "Tarea no disponible" }) : task(),
    "PUT /api/tareas/1": reply(400, { message: "Datos inválidos", fieldErrors: [{ field: "nombre", message: "Nombre rechazado" }] }),
  });
  render(<TaskFormDialog taskId={1} canUpload onClose={vi.fn()} onSaved={vi.fn()} />);
  const user = userEvent.setup();
  await screen.findByText("Tarea no disponible");
  fail = false;
  await user.click(screen.getByText("Reintentar carga"));
  await screen.findByLabelText(/^Nombre/);
  await user.click(screen.getByText("Guardar cambios"));
  expect(await screen.findByText("Nombre rechazado")).toBeTruthy();
});

test("Sube nuevos adjuntos una vez, conserva los existentes y omite los retirados", async () => {
  const calls = backend({ "GET /api/tareas/1": task({ archivos: [attachment(), attachment(10)] }),
    "PUT /api/tareas/1": reply(422, { message: "Revisa los datos" }) });
  const ordinaryFetch = globalThis.fetch;
  const uploads = [];
  vi.stubGlobal("fetch", vi.fn(async (url, options) => {
    if (new URL(url).pathname !== "/api/archivos") return ordinaryFetch(url, options);
    uploads.push(options);
    return { ok: true, status: 201, json: async () => attachment(11) };
  }));
  render(<TaskFormDialog taskId={1} canUpload onClose={vi.fn()} onSaved={vi.fn()} />);
  const user = userEvent.setup();
  await user.click(await screen.findByLabelText("Quitar guia-10.pdf"));
  await user.upload(screen.getByLabelText("Adjuntos de la tarea"), new File(["%PDF-content"], "nuevo.pdf", { type: "application/pdf" }));
  await user.click(screen.getByText("Guardar cambios"));
  await screen.findByText("Revisa los datos");
  expect(uploads[0].body.get("archivo").name).toBe("nuevo.pdf");
  expect(uploads[0].headers.get("Content-Type")).toBeNull();
  expect(calls.find((call) => call.method === "PUT").body.archivoIds).toEqual([9, 11]);
  await user.click(screen.getByText("Guardar cambios"));
  await waitFor(() => expect(calls.filter((call) => call.method === "PUT")).toHaveLength(2));
  expect(uploads).toHaveLength(1);
  expect(calls.some((call) => call.method === "DELETE")).toBe(false);
});

test("No guarda tras fallar una subida y permite quitar el archivo pendiente", async () => {
  const calls = backend();
  const ordinaryFetch = globalThis.fetch;
  vi.stubGlobal("fetch", vi.fn(async (url, options) => new URL(url).pathname === "/api/archivos"
    ? { ok: false, status: 422, json: async () => ({ code: "ARCHIVO_TIPO_NO_PERMITIDO", message: "Formato real no permitido" }) }
    : ordinaryFetch(url, options)));
  render(<TaskFormDialog taskId={1} canUpload onClose={vi.fn()} onSaved={vi.fn()} />);
  const user = userEvent.setup();
  await screen.findByLabelText(/^Nombre/);
  await user.upload(screen.getByLabelText("Adjuntos de la tarea"), new File(["test"], "nuevo.pdf", { type: "application/pdf" }));
  await user.click(screen.getByText("Guardar cambios"));
  await screen.findByText("Formato real no permitido");
  expect(calls.some((call) => call.method === "PUT")).toBe(false);
  await user.click(screen.getByLabelText("Quitar nuevo.pdf"));
  expect(screen.queryByText(/nuevo.pdf/)).toBeNull();
});

test("Alta preparada recibe contexto externo y no envía estado ni versión", async () => {
  const calls = backend({ "POST /api/tareas": ({ body }) => task(body) });
  const onSaved = vi.fn();
  render(<TaskFormDialog asignacionCursoId={42} canUpload={false} onClose={vi.fn()} onSaved={onSaved} />);
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(/^Nombre/), "Borrador nuevo");
  await user.type(screen.getByLabelText(/^Descripción/), "Descripción nueva");
  // datetime-local se completa por change porque user-event no simula este control.
  const { fireEvent } = await import("@testing-library/react");
  fireEvent.change(screen.getByLabelText(/^Fecha límite/), { target: { value: "2099-12-01T18:30" } });
  expect(screen.getByLabelText("Adjuntos de la tarea").disabled).toBe(true);
  await user.click(screen.getByText("Guardar borrador"));
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
  const body = calls.find((call) => call.method === "POST").body;
  expect(body.asignacionCursoId).toBe(42);
  expect(body.archivoIds).toEqual([]);
  expect(body.estado).toBeUndefined();
  expect(body.version).toBeUndefined();
});

test("Una subida parcial se reintenta sin repetir archivos correctos y bloquea el formulario", async () => {
  const calls = backend();
  const ordinaryFetch = globalThis.fetch;
  const uploads = [];
  let release;
  vi.stubGlobal("fetch", vi.fn(async (url, options) => {
    if (new URL(url).pathname !== "/api/archivos") return ordinaryFetch(url, options);
    const name = options.body.get("archivo").name;
    uploads.push(name);
    if (uploads.length === 1) await new Promise((resolve) => { release = resolve; });
    if (uploads.length === 2) return { ok: false, status: 500, json: async () => ({ message: "Subida interrumpida" }) };
    return { ok: true, status: 201, json: async () => attachment(name === "uno.pdf" ? 11 : 12) };
  }));
  const onSaved = vi.fn();
  render(<TaskFormDialog taskId={1} canUpload onClose={vi.fn()} onSaved={onSaved} />);
  const user = userEvent.setup();
  await screen.findByLabelText(/^Nombre/);
  await user.upload(screen.getByLabelText("Adjuntos de la tarea"), [new File(["a"], "uno.pdf", { type: "application/pdf" }), new File(["b"], "dos.pdf", { type: "application/pdf" })]);
  await user.click(screen.getByText("Guardar cambios"));
  await waitFor(() => expect(release).toBeTypeOf("function"));
  expect(screen.getByText("Guardando…").disabled).toBe(true);
  expect(screen.getByText("Cancelar").disabled).toBe(true);
  const { act } = await import("@testing-library/react");
  await act(async () => { release(); });
  await screen.findByText("Subida interrumpida");
  expect(calls.some((call) => call.method === "PUT")).toBe(false);
  await user.click(screen.getByText("Guardar cambios"));
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
  expect(uploads).toEqual(["uno.pdf", "dos.pdf", "dos.pdf"]);
  expect(calls.find((call) => call.method === "PUT").body.archivoIds).toEqual([9, 11, 12]);
});

test("Reglas: fechas históricas explícitas, límites y archivos inválidos", () => {
  const fields = { nombre: "Tarea", descripcion: "Descripción", fechaPublicacion: "2020-01-01T12:00", fechaLimite: "2020-01-02T12:00", puntajeMaximo: "1000", maxArchivosEntrega: "0" };
  expect(validateTask(fields, 20)).toEqual({});
  expect(validateTask({ ...fields, fechaPublicacion: "" }, 20).fechaLimite).toBeTruthy();
  expect(validateTask({ ...fields, fechaPublicacion: fields.fechaLimite, puntajeMaximo: "1.5", maxArchivosEntrega: "21" }, 21)).toHaveProperty("archivoIds");
  expect(fileError(new File([], "vacio.pdf"))).toContain("vacío");
  expect(fileError(new File(["test"], "texto.txt"))).toContain("Selecciona");
  expect(fileError({ size: 10485761, name: "grande.pdf" })).toContain("10 MiB");
});

test("Fechas: mensajes específicos, igualdad rechazada y error en publicación", () => {
  const fields = { nombre: "Tarea", descripcion: "Descripción", fechaPublicacion: "", fechaLimite: "2026-10-29T12:30:00", puntajeMaximo: "20", maxArchivosEntrega: "0" };
  const now = new Date("2026-10-29T12:30:00");
  expect(validateTask(fields, 0, now).fechaLimite).toBe("La fecha límite debe ser posterior al momento actual.");
  expect(validateTask({ ...fields, fechaLimite: "2026-10-29T12:30:01" }, 0, now)).toEqual({});
  expect(validateTask({ ...fields, fechaPublicacion: fields.fechaLimite }, 0, now).fechaLimite).toBe("La fecha límite debe ser posterior a la fecha de publicación.");
  const invalid = validateTask({ ...fields, fechaPublicacion: "inválida" }, 0, now);
  expect(invalid.fechaPublicacion).toBe("Indica una fecha de publicación válida.");
  expect(invalid.fechaLimite).toBeUndefined();
});

test("El modal muestra contexto solo informativo y permite editar si falla su consulta", async () => {
  backend({ "GET /api/asignaciones-cursos/42": reply(403) });
  render(<TaskFormDialog taskId={1} canUpload onClose={vi.fn()} onSaved={vi.fn()} />);
  const group = await screen.findByRole("group", { name: "Curso y sección asociados" });
  expect(await within(group).findByText(/Curso y sección no disponibles/)).toBeTruthy();
  expect(within(group).queryByRole("combobox")).toBeNull();
  expect(screen.getByText("Guardar cambios").disabled).toBe(false);
});

test("El modal resuelve el curso y sección asociados al detalle", async () => {
  const calls = backend();
  render(<TaskFormDialog taskId={1} canUpload onClose={vi.fn()} onSaved={vi.fn()} />);
  const group = await screen.findByRole("group", { name: "Curso y sección asociados" });
  expect(await within(group).findByText("Matemática")).toBeTruthy();
  expect(within(group).getByText("A")).toBeTruthy();
  expect(calls.filter((call) => call.path.startsWith("/api/asignaciones-cursos")).map((call) => call.path)).toEqual(["/api/asignaciones-cursos/42"]);
});

test("Agregar archivos funciona con teclado, mantiene selección múltiple, foco y disabled", async () => {
  const select = vi.fn();
  const props = { existing: [], pending: [], disabled: false, canUpload: true, error: "Archivo rechazado", onSelect: select, onRemoveExisting: vi.fn(), onRemoveNew: vi.fn() };
  const { rerender } = render(<TaskAttachments {...props} />);
  const user = userEvent.setup();
  const input = screen.getByLabelText("Adjuntos de la tarea");
  const click = vi.spyOn(input, "click");
  const button = screen.getByRole("button", { name: "Agregar archivos" });
  button.focus();
  await user.keyboard("{Enter}");
  expect(click).toHaveBeenCalledOnce();
  expect(button.getAttribute("aria-describedby")).toContain(screen.getByRole("alert").id);
  await user.upload(input, [new File(["a"], "a.pdf", { type: "application/pdf" }), new File(["b"], "b.pdf", { type: "application/pdf" })]);
  expect(select.mock.calls[0][0]).toHaveLength(2);
  expect(document.activeElement).toBe(button);
  expect(input.value).toBe("");
  rerender(<TaskAttachments {...props} disabled />);
  expect(button.disabled).toBe(true);
  expect(input.disabled).toBe(true);
  rerender(<TaskAttachments {...props} canUpload={false} />);
  expect(button.disabled).toBe(true);
});
