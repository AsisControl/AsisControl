import { beforeEach, expect, test, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { TaskDeliveries } from "../task-deliveries";
import { DeliveryGradeDialog } from "../delivery-grade-dialog";
import { Tasks } from "@/modules/tasks";
import { ModuleOutlet } from "@/core/navigation/module-outlet";
import { SessionUserProvider } from "@/core/layout/session-user";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));
vi.mock("next/link", () => import("../../../../tests/support/next-link.jsx"));

const task = (overrides = {}) => ({ id: 1, asignacionCursoId: 42, nombre: "Fracciones", descripcion: "Resolver ejercicios", fechaPublicacion: "2026-10-01T12:00:00", fechaLimite: "2026-10-10T18:00:00", puntajeMaximo: 20, maxArchivosEntrega: 2, estado: "PUBLICADA", archivos: [], version: 3, ...overrides });
const file = { id: 80, nombreOriginal: "respuesta.pdf", tipoMime: "application/pdf", tamanioBytes: 10, sha256: "hash", cargadoPorPersonaId: 5, fechaSubida: "2026-10-11T12:00:00Z" };
const delivery = (overrides = {}) => ({ id: 10, tareaId: 1, alumnoId: 8, fechaHoraEntrega: "2026-10-11T12:30:45", archivos: [file], nota: null, comentario: "Mi respuesta", estado: "ENTREGADO_TARDE", version: 4, ...overrides });
const student = (id = 8) => ({ id, codigoAlumno: `AL-${id}`, persona: { nombreCompleto: id === 8 ? "Lucía Pérez" : "Luis Ramos" } });
function backend(routes = {}) {
  return fakeBackend({
    "GET /api/tareas/1": task(),
    "GET /api/tareas/1/entregas": page([delivery()]),
    "GET /api/entregas/10": delivery(),
    "GET /api/alumnos/:id": ({ path }) => student(Number(path.split("/").at(-1))),
    "POST /api/entregas/10/calificar": ({ body }) => delivery({ ...body, estado: "CALIFICADO", version: 5 }),
    ...routes,
  });
}
beforeEach(() => {
  signIn({ role: "DOCENTE", permissions: ["TAREAS_LEER", "TAREAS_ESCRIBIR"] });
  setUrl("/tareas/1/entregas");
});

test("Lista solo entregas recibidas con identidad, fecha, estado, comentario, nota y adjuntos", async () => {
  const calls = backend();
  render(<TaskDeliveries taskId={1} canWrite />);
  expect(screen.getByRole("status").textContent).toBe("Cargando tarea…");
  expect(await screen.findByText("Lucía Pérez")).toBeTruthy();
  expect(screen.getByText("AL-8")).toBeTruthy();
  expect(screen.getByText("11/10/2026 12:30:45").getAttribute("datetime")).toBe("2026-10-11T12:30:45");
  expect(screen.getByText("Entregado tarde")).toBeTruthy();
  expect(screen.getByText("Mi respuesta")).toBeTruthy();
  expect(screen.getByText("Sin calificar")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Descargar respuesta.pdf" })).toBeTruthy();
  expect(screen.getByRole("button", { name: "Calificar Lucía Pérez" })).toBeTruthy();
  expect(screen.getByRole("link", { name: "Volver a tareas" }).getAttribute("href")).toBe("/tareas");
  expect(screen.getByRole("link", { name: "Volver a tareas" }).textContent).toBe("← Volver a tareas");
  expect(calls.find((call) => call.path.endsWith("/entregas")).query).toEqual({ page: "0", size: "20" });
  expect(calls.some((call) => call.path === "/api/alumnos" || call.path === "/api/matriculas")).toBe(false);
  expect(screen.queryByText(/Sin entrega|\d+ de \d+ estudiantes|%/)).toBeNull();
});

test("Paginación y actualización conservan caché de alumnos ya resueltos", async () => {
  const calls = backend({ "GET /api/tareas/1/entregas": ({ query }) => ({
    content: [delivery(query.page === "1" ? { id: 11, alumnoId: 9 } : {})],
    number: Number(query.page), totalPages: 2, totalElements: 21,
  }) });
  const user = userEvent.setup();
  render(<TaskDeliveries taskId={1} canWrite={false} />);
  await screen.findByText("Lucía Pérez");
  await user.click(screen.getByText("Siguiente"));
  await screen.findByText("Luis Ramos");
  expect(screen.getByText("21 registros · Página 2 de 2")).toBeTruthy();
  await user.click(screen.getByText("Anterior"));
  await screen.findByText("Lucía Pérez");
  await user.click(screen.getByText("Actualizar"));
  await waitFor(() => expect(calls.filter((call) => call.path.endsWith("/entregas"))).toHaveLength(4));
  await screen.findByText("Lucía Pérez");
  expect(calls.filter((call) => call.path === "/api/alumnos/8")).toHaveLength(1);
  expect(calls.filter((call) => call.path === "/api/alumnos/9")).toHaveLength(1);
});

test("El fallo de identidad muestra fallback y se reintenta al actualizar", async () => {
  let fail = true;
  backend({ "GET /api/alumnos/:id": () => fail ? reply(403) : student() });
  render(<TaskDeliveries taskId={1} canWrite />);
  await screen.findByText("Alumno #8");
  fail = false;
  await userEvent.setup().click(screen.getByText("Actualizar"));
  expect(await screen.findByText("Lucía Pérez")).toBeTruthy();
});

test.each(["PUBLICADA", "CERRADA"])("Califica y recalifica en %s con nota cero, comentario y versión exacta", async (estado) => {
  let current = delivery();
  const calls = backend({
    "GET /api/tareas/1": task({ estado }),
    "GET /api/tareas/1/entregas": () => page([current]),
    "GET /api/entregas/10": () => current,
    "POST /api/entregas/10/calificar": ({ body }) => { current = delivery({ ...body, estado: "CALIFICADO", version: current.version + 1 }); return current; },
  });
  render(<TaskDeliveries taskId={1} canWrite />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "Calificar Lucía Pérez" }));
  const nota = await screen.findByLabelText(/^Nota/);
  await user.type(nota, "0");
  const comment = screen.getByLabelText("Comentario");
  expect(comment.value).toBe("Mi respuesta");
  await user.clear(comment);
  await user.type(comment, " Revisado ");
  await user.click(screen.getByText("Guardar calificación"));
  await screen.findByText("Calificación guardada.");
  expect(calls.find((call) => call.method === "POST").body).toEqual({ nota: 0, comentario: "Revisado", version: 4 });
  await screen.findByText("0 / 20");
  await user.click(await screen.findByRole("button", { name: "Modificar calificación de Lucía Pérez" }));
  const newNota = await screen.findByLabelText(/^Nota/);
  expect(newNota.value).toBe("0");
  await user.clear(newNota);
  await user.type(newNota, "20");
  await user.click(screen.getByText("Guardar calificación"));
  await screen.findByText("20 / 20");
  expect(calls.filter((call) => call.method === "POST").at(-1).body.version).toBe(5);
});

test("Archivar, falta de escritura y entrega anulada impiden calificar", async () => {
  backend({ "GET /api/tareas/1": task({ estado: "ARCHIVADA" }), "GET /api/tareas/1/entregas": page([delivery({ nota: 18, estado: "CALIFICADO" })]) });
  const view = render(<TaskDeliveries taskId={1} canWrite />);
  await screen.findByText("18 / 20");
  expect(screen.getByText("Tarea archivada: consulta de solo lectura.")).toBeTruthy();
  expect(screen.queryByRole("button", { name: /Calificar|Modificar calificación/ })).toBeNull();
  view.unmount();
  backend({ "GET /api/tareas/1/entregas": page([delivery({ estado: "ANULADO" })]) });
  const second = render(<TaskDeliveries taskId={1} canWrite />);
  await screen.findByText("Anulado");
  expect(screen.queryByRole("button", { name: /Calificar/ })).toBeNull();
  second.unmount();
  backend();
  render(<TaskDeliveries taskId={1} canWrite={false} />);
  await screen.findByText("Lucía Pérez");
  expect(screen.queryByRole("button", { name: /Calificar/ })).toBeNull();
});

test("Valida nota vacía, negativa, decimal y superior al máximo antes del POST", async () => {
  const calls = backend();
  render(<DeliveryGradeDialog taskId={1} deliveryId={10} student="Lucía" onClose={vi.fn()} onRefresh={vi.fn()} onSaved={vi.fn()} />);
  const input = await screen.findByLabelText(/^Nota/);
  const user = userEvent.setup();
  for (const value of ["", "-1", "1.5", "21"]) {
    fireEvent.change(input, { target: { value } });
    await user.click(screen.getByText("Guardar calificación"));
    expect(screen.getByText("Introduce una nota entera entre 0 y 20.")).toBeTruthy();
  }
  expect(calls.some((call) => call.method === "POST")).toBe(false);
});

test("Conflicto de versión exige recarga explícita y usa la versión nueva", async () => {
  let current = delivery();
  let attempts = 0;
  const calls = backend({ "GET /api/entregas/10": () => current,
    "POST /api/entregas/10/calificar": () => ++attempts === 1 ? reply(409, { code: "VERSION_DESACTUALIZADA" }) : current });
  const saved = vi.fn();
  render(<DeliveryGradeDialog taskId={1} deliveryId={10} student="Lucía" onClose={vi.fn()} onRefresh={vi.fn()} onSaved={saved} />);
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText(/^Nota/), "15");
  await user.click(screen.getByText("Guardar calificación"));
  await screen.findByText(/La entrega cambió/);
  expect(screen.queryByText("Guardar calificación")).toBeNull();
  expect(attempts).toBe(1);
  current = delivery({ version: 9, nota: 12 });
  await user.click(screen.getByText("Descartar cambios y recargar versión actual"));
  expect((await screen.findByLabelText(/^Nota/)).value).toBe("12");
  await user.click(screen.getByText("Guardar calificación"));
  await waitFor(() => expect(saved).toHaveBeenCalledOnce());
  expect(calls.filter((call) => call.method === "POST").at(-1).body.version).toBe(9);
});

test.each([
  ["NOTA_FUERA_DE_RANGO", /La nota no puede superar/, false],
  ["TAREA_NO_CALIFICABLE", /La tarea ya no permite calificar/, true],
  ["ENTREGA_ANULADA", /La entrega está anulada/, true],
  ["ENTREGA_NO_ENCONTRADA", /La entrega ya no está disponible/, true],
])("Gestiona %s sin éxito falso ni reenvío automático", async (code, expected, blocked) => {
  const calls = backend({ "POST /api/entregas/10/calificar": reply(code === "ENTREGA_NO_ENCONTRADA" ? 404 : 422, { code }) });
  const saved = vi.fn(), refresh = vi.fn();
  render(<DeliveryGradeDialog taskId={1} deliveryId={10} student="Lucía" onClose={vi.fn()} onRefresh={refresh} onSaved={saved} />);
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText(/^Nota/), "18");
  await user.click(screen.getByText("Guardar calificación"));
  expect((await screen.findByRole("alert")).textContent).toMatch(expected);
  expect(!!screen.queryByText("Guardar calificación")).toBe(!blocked);
  expect(saved).not.toHaveBeenCalled();
  expect(calls.filter((call) => call.method === "POST")).toHaveLength(1);
  await user.click(screen.getByText(blocked ? "Actualizar seguimiento" : "Cancelar y actualizar seguimiento"));
  expect(refresh).toHaveBeenCalledOnce();
});

test("Descarga autenticada con nombre original y libera la URL; informa errores", async () => {
  const StandardURL = globalThis.URL;
  vi.stubGlobal("URL", class extends StandardURL {
    static createObjectURL = vi.fn(() => "blob:entrega");
    static revokeObjectURL = vi.fn();
  });
  const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  backend();
  const ordinaryFetch = globalThis.fetch;
  let fail = false;
  const requests = [];
  vi.stubGlobal("fetch", vi.fn(async (url, options) => {
    if (!url.endsWith("/api/archivos/80/descarga")) return ordinaryFetch(url, options);
    requests.push(options);
    return fail ? { ok: false, status: 403, json: async () => ({ message: "Sin acceso al archivo" }) }
      : { ok: true, status: 200, blob: async () => new Blob(["%PDF"], { type: "application/pdf" }) };
  }));
  render(<TaskDeliveries taskId={1} canWrite={false} />);
  const user = userEvent.setup();
  const download = await screen.findByRole("button", { name: "Descargar respuesta.pdf" });
  await user.click(download);
  await waitFor(() => expect(click).toHaveBeenCalledOnce());
  expect(click.mock.instances[0].download).toBe("respuesta.pdf");
  expect(requests[0].headers.get("Authorization")).toBe("Bearer test-token");
  expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:entrega");
  fail = true;
  await user.click(download);
  expect((await screen.findByRole("alert")).textContent).toContain("Sin acceso al archivo");
});

test("Vacío y error del listado permiten actualización, sin filas ficticias", async () => {
  let fail = true;
  backend({ "GET /api/tareas/1/entregas": () => fail ? reply(500, { message: "No se pudieron consultar entregas" }) : page([]) });
  render(<TaskDeliveries taskId={1} canWrite={false} />);
  await screen.findByText("No se pudieron consultar entregas");
  expect(screen.queryByRole("table")).toBeNull();
  fail = false;
  await userEvent.setup().click(screen.getByText("Actualizar"));
  expect(await screen.findByText("Esta tarea aún no tiene entregas recibidas.")).toBeTruthy();
});

test("Reintenta tarea inaccesible sin consultar alumnos o entregas antes", async () => {
  let fail = true;
  const calls = backend({ "GET /api/tareas/1": () => fail ? reply(403, { message: "No autorizada" }) : task() });
  render(<TaskDeliveries taskId={1} canWrite />);
  await screen.findByText("No autorizada");
  expect(calls).toHaveLength(1);
  fail = false;
  await userEvent.setup().click(screen.getByText("Reintentar carga"));
  await screen.findByText("Lucía Pérez");
});

test("Si la tarea se archivó o la entrega se anuló antes de abrir, bloquea el formulario", async () => {
  backend({ "GET /api/tareas/1": task({ estado: "ARCHIVADA" }) });
  render(<DeliveryGradeDialog taskId={1} deliveryId={10} student="Lucía" onClose={vi.fn()} onRefresh={vi.fn()} onSaved={vi.fn()} />);
  expect(await screen.findByText(/Solo se pueden calificar entregas no anuladas/)).toBeTruthy();
  expect(screen.queryByText("Guardar calificación")).toBeNull();
});

test("Bloquea doble envío mientras guarda y admite comentario vacío como null", async () => {
  let release;
  const calls = backend({ "POST /api/entregas/10/calificar": () => new Promise((resolve) => { release = resolve; }) });
  const saved = vi.fn();
  render(<DeliveryGradeDialog taskId={1} deliveryId={10} student="Lucía" onClose={vi.fn()} onRefresh={vi.fn()} onSaved={saved} />);
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText(/^Nota/), "18");
  await user.clear(screen.getByLabelText("Comentario"));
  await user.dblClick(screen.getByText("Guardar calificación"));
  expect(screen.getByText("Guardando…").disabled).toBe(true);
  expect(screen.getByText("Cancelar").disabled).toBe(true);
  expect(calls.filter((call) => call.method === "POST")).toHaveLength(1);
  expect(calls.find((call) => call.method === "POST").body.comentario).toBeNull();
  await act(async () => { release(delivery({ nota: 18 })); });
  expect(saved).toHaveBeenCalledOnce();
});

test("Acceso Entregas por estado y ruta protegida por TAREAS_LEER", async () => {
  backend({ "GET /api/tareas": page(["BORRADOR", "PUBLICADA", "CERRADA", "ARCHIVADA"].map((estado, id) => task({ id: id + 1, nombre: estado, estado }))) });
  const view = render(<Tasks />);
  await screen.findByRole("link", { name: "Entregas de PUBLICADA" });
  expect(screen.queryByRole("link", { name: "Entregas de BORRADOR" })).toBeNull();
  expect(screen.getAllByRole("link", { name: /Entregas de/ })).toHaveLength(3);
  expect(screen.getByRole("link", { name: "Entregas de ARCHIVADA" }).getAttribute("href")).toBe("/tareas/4/entregas");
  view.unmount();
  const calls = backend();
  const user = { id: "99", role: "DOCENTE", fullName: "Ana", permissions: [] };
  const root = render(<SessionUserProvider value={user}><ModuleOutlet /></SessionUserProvider>);
  expect(screen.getByText("No tienes permiso para acceder a esta opción.")).toBeTruthy();
  expect(calls).toHaveLength(0);
  root.rerender(<SessionUserProvider value={{ ...user, permissions: ["TAREAS_LEER"] }}><ModuleOutlet /></SessionUserProvider>);
  await screen.findByText("Lucía Pérez");
  expect(screen.queryByRole("button", { name: /Calificar/ })).toBeNull();
});

test("Ruta inválida no consulta la API; borrador no consulta entregas", async () => {
  setUrl("/tareas/no/entregas");
  const calls = backend({ "GET /api/tareas/1": task({ estado: "BORRADOR" }) });
  const user = { id: "99", role: "DOCENTE", permissions: ["TAREAS_LEER"] };
  render(<SessionUserProvider value={user}><ModuleOutlet /></SessionUserProvider>);
  expect(screen.getByText("La dirección de seguimiento no es válida.")).toBeTruthy();
  expect(calls).toHaveLength(0);
  await act(async () => setUrl("/tareas/1/entregas"));
  expect(await screen.findByText(/El seguimiento está disponible/)).toBeTruthy();
  expect(calls.some((call) => call.path.endsWith("/entregas"))).toBe(false);
});
