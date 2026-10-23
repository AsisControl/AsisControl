import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { Academic } from "@/modules/academic";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const competency = (overrides = {}) => ({
  id: 5,
  codigo: "MAT-C1",
  nombre: "Resuelve problemas de cantidad",
  descripcion: "Traduce situaciones a expresiones numéricas",
  activo: true,
  version: 1,
  ...overrides,
});
const reading = competency({ id: 6, codigo: "COM-C1", nombre: "Lee textos diversos", descripcion: null });

const link = (id, cursoId, cursoNombre, competenciaId, orden) => ({
  id,
  cursoId,
  cursoCodigo: cursoNombre.slice(0, 3).toUpperCase(),
  cursoNombre,
  competenciaId,
  competenciaCodigo: "MAT-C1",
  competenciaNombre: "Resuelve problemas de cantidad",
  orden,
  activo: true,
  version: 0,
});

function backend(routes = {}) {
  return fakeBackend({
    "GET /api/competencias": page([competency(), reading]),
    "GET /api/cursos-competencias": page([
      link(1, 1, "Matemática", 5, 1),
      link(2, 2, "Ciencia", 5, 3),
    ]),
    ...routes,
  });
}

const renderTab = (canWrite = true) =>
  render(<Academic canWrite={canWrite} tab="competencias" />);
const rowOf = async (name) => (await screen.findByText(name, { selector: "strong" })).closest("tr");

beforeEach(() => {
  signIn({ permissions: ["ACADEMICO_LEER", "ACADEMICO_ESCRIBIR"] });
  setUrl("/academico/competencias");
});

test("Lista competencias con descripción y cantidad de cursos", async () => {
  backend();
  renderTab();
  const quantity = await rowOf("Resuelve problemas de cantidad");
  expect(within(quantity).getByText("MAT-C1")).toBeTruthy();
  expect(await within(quantity).findByText("2 cursos")).toBeTruthy();
  expect(within(quantity).getByText("Activa")).toBeTruthy();
  const readingRow = await rowOf("Lee textos diversos");
  expect(within(readingRow).getByText("—")).toBeTruthy();
  expect(within(readingRow).getByText("0 cursos")).toBeTruthy();
  expect(screen.getByText(/2 competencias en el sistema/)).toBeTruthy();
});

test("Busca con el parámetro busqueda del backend", async () => {
  const calls = backend();
  const user = userEvent.setup();
  renderTab();
  await rowOf("Lee textos diversos");
  await user.type(screen.getByLabelText("Buscar competencias"), " lee ");
  await waitFor(() =>
    expect(calls.some((c) => c.path === "/api/competencias" && c.query.busqueda === "lee")).toBe(true),
  );
  expect(await screen.findByText(/encontradas/)).toBeTruthy();
});

test("Sin resultados de búsqueda lo indica", async () => {
  backend({
    "GET /api/competencias": ({ query }) => page(query.busqueda ? [] : [competency()]),
  });
  const user = userEvent.setup();
  renderTab();
  await rowOf("Resuelve problemas de cantidad");
  await user.type(screen.getByLabelText("Buscar competencias"), "zzz");
  expect(await screen.findByText("No hay competencias que coincidan con la búsqueda.")).toBeTruthy();
});

test("El detalle muestra los cursos que la trabajan y su orden", async () => {
  backend();
  const user = userEvent.setup();
  renderTab();
  await screen.findByText("2 cursos");
  await user.click(within(await rowOf("Resuelve problemas de cantidad")).getByText("Ver detalle"));
  const dialog = screen.getByRole("dialog", { name: "Resuelve problemas de cantidad" });
  expect(within(dialog).getByText("Matemática")).toBeTruthy();
  expect(within(dialog).getByText("CIE · Orden 3 en el curso")).toBeTruthy();
  await user.click(within(dialog).getByText("Editar competencia"));
  expect(screen.getByRole("dialog", { name: "Editar competencia" })).toBeTruthy();
});

test("Una competencia sin cursos ni descripción lo indica en el detalle", async () => {
  backend();
  const user = userEvent.setup();
  renderTab(false);
  await user.click(within(await rowOf("Lee textos diversos")).getByText("Ver detalle"));
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByText("Sin descripción")).toBeTruthy();
  expect(within(dialog).getByText("Todavía no está asociada a ningún curso.")).toBeTruthy();
  expect(within(dialog).queryByText("Editar competencia")).toBeNull();
  expect(screen.queryByText("+ Nueva competencia")).toBeNull();
});

test("Crea una competencia", async () => {
  const calls = backend({
    "POST /api/competencias": ({ body }) => reply(201, competency({ id: 9, ...body })),
  });
  const user = userEvent.setup();
  renderTab();
  await rowOf("Lee textos diversos");
  await user.click(screen.getByText("+ Nueva competencia"));
  const dialog = screen.getByRole("dialog", { name: "Nueva competencia" });
  await user.type(within(dialog).getByLabelText(/^Nombre de la competencia/), " Escribe textos ");
  await user.type(within(dialog).getByLabelText(/^Código/), "COM-C2");
  await user.type(within(dialog).getByLabelText(/^Descripción/), "  Redacta con propósito ");
  await user.click(within(dialog).getByRole("button", { name: "Crear competencia" }));
  expect(await screen.findByText("Competencia Escribe textos creada.")).toBeTruthy();
  expect(calls.find((c) => c.method === "POST").body).toEqual({
    codigo: "COM-C2",
    nombre: "Escribe textos",
    descripcion: "Redacta con propósito",
  });
});

test("Edita una competencia con su versión y muestra los conflictos", async () => {
  const calls = backend({
    "PUT /api/competencias/:id": ({ body }) =>
      body.codigo === "COM-C1"
        ? reply(409, { message: "Ya existe una competencia con ese codigo" })
        : competency({ ...body, version: 2 }),
  });
  const user = userEvent.setup();
  renderTab();
  await user.click(await screen.findByLabelText("Editar Resuelve problemas de cantidad"));
  const dialog = screen.getByRole("dialog", { name: "Editar competencia" });
  const code = within(dialog).getByLabelText(/^Código/);
  await user.clear(code);
  await user.type(code, "COM-C1");
  await user.click(within(dialog).getByRole("button", { name: "Guardar cambios" }));
  expect((await within(dialog).findByRole("alert")).textContent).toBe(
    "Ya existe una competencia con ese codigo",
  );
  await user.clear(code);
  await user.type(code, "MAT-C9");
  await user.click(within(dialog).getByRole("button", { name: "Guardar cambios" }));
  expect(await screen.findByText("Competencia Resuelve problemas de cantidad actualizada.")).toBeTruthy();
  expect(calls.filter((c) => c.method === "PUT").at(-1).body).toEqual({
    codigo: "MAT-C9",
    nombre: "Resuelve problemas de cantidad",
    descripcion: "Traduce situaciones a expresiones numéricas",
    version: 1,
  });
});

test("Desactiva una competencia avisando cuántos cursos la usan", async () => {
  const calls = backend({ "DELETE /api/competencias/:id": reply(204) });
  const user = userEvent.setup();
  renderTab();
  await screen.findByText("2 cursos");
  await user.click(screen.getByLabelText("Más acciones para Resuelve problemas de cantidad"));
  await user.click(screen.getByRole("menuitem", { name: "Desactivar" }));
  const dialog = screen.getByRole("dialog", { name: "Desactivar competencia" });
  expect(within(dialog).getByText(/\(2 cursos\)/)).toBeTruthy();
  await user.click(within(dialog).getByRole("button", { name: "Desactivar competencia" }));
  expect(await screen.findByText("Competencia Resuelve problemas de cantidad desactivada.")).toBeTruthy();
  expect(calls.find((c) => c.method === "DELETE").path).toBe("/api/competencias/5");
});

test("Un error al desactivar se muestra y Cancelar cierra la ventana", async () => {
  backend({ "DELETE /api/competencias/:id": reply(404, { message: "No existe una competencia activa" }) });
  const user = userEvent.setup();
  renderTab();
  await user.click(await screen.findByLabelText("Más acciones para Lee textos diversos"));
  await user.click(screen.getByRole("menuitem", { name: "Desactivar" }));
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).queryByText(/cursos\)/)).toBeNull();
  await user.click(within(dialog).getByRole("button", { name: "Desactivar competencia" }));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("No existe una competencia activa");
  await user.click(within(dialog).getByText("Cancelar"));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Si fallan las asociaciones los cursos se muestran como no disponibles", async () => {
  backend({ "GET /api/cursos-competencias": reply(500, null) });
  const user = userEvent.setup();
  renderTab();
  expect(await screen.findByText("No se pudieron consultar los cursos asociados.")).toBeTruthy();
  await user.click(within(await rowOf("Lee textos diversos")).getByText("Ver detalle"));
  expect(within(screen.getByRole("dialog")).getByText("No se pudieron consultar los cursos asociados.")).toBeTruthy();
});

test("Sin competencias y con error del listado", async () => {
  const calls = backend({ "GET /api/competencias": page([]) });
  const user = userEvent.setup();
  const { unmount } = renderTab();
  expect(await screen.findByText("Aún no hay competencias registradas.")).toBeTruthy();
  await user.click(screen.getByText("Actualizar"));
  await waitFor(() => expect(calls.filter((c) => c.path === "/api/competencias")).toHaveLength(2));
  unmount();
  backend({ "GET /api/competencias": reply(500, { message: "Catálogo no disponible" }) });
  renderTab();
  expect((await screen.findByRole("alert")).textContent).toBe("Catálogo no disponible");
});
