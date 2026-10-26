import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { Academic } from "@/modules/academic";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const courses = [
  { id: 1, codigo: "MAT", nombre: "Matemática" },
  { id: 2, codigo: "CIE", nombre: "Ciencia" },
];
const competencies = [
  { id: 5, codigo: "C1", nombre: "Resuelve problemas de cantidad" },
  { id: 6, codigo: "C2", nombre: "Resuelve problemas de forma" },
  { id: 7, codigo: "C3", nombre: "Indaga con métodos científicos" },
];
const link = (id, curso, competencia, orden, overrides = {}) => ({
  id,
  cursoId: curso.id,
  cursoCodigo: curso.codigo,
  cursoNombre: curso.nombre,
  competenciaId: competencia.id,
  competenciaCodigo: competencia.codigo,
  competenciaNombre: competencia.nombre,
  orden,
  activo: true,
  version: 4,
  ...overrides,
});
const [math, science] = courses;
const [quantity, shape, inquiry] = competencies;

function backend(routes = {}) {
  return fakeBackend({
    "GET /api/cursos-competencias": page([
      link(2, math, shape, 2),
      link(1, math, quantity, 1),
      link(3, science, inquiry, 1),
      link(4, science, quantity, 9, { activo: false }),
    ]),
    "GET /api/cursos": page(courses),
    "GET /api/competencias": page(competencies),
    ...routes,
  });
}

const renderTab = (canWrite = true) =>
  render(<Academic canWrite={canWrite} tab="asociaciones" />);
const rows = () =>
  screen.getAllByRole("row").slice(1).map((row) =>
    [...row.querySelectorAll("td")].slice(0, 3).map((cell) => cell.textContent).join(" | "),
  );

beforeEach(() => {
  signIn({ permissions: ["ACADEMICO_LEER", "ACADEMICO_ESCRIBIR"] });
  setUrl("/academico/asociaciones");
});

test("Lista las asociaciones vigentes por curso y orden, y filtra por curso", async () => {
  const calls = backend();
  const user = userEvent.setup();
  renderTab();
  await screen.findByText("3 asociaciones", { exact: false });
  expect(rows()).toEqual([
    "CienciaCIE | 1 | Indaga con métodos científicosC3",
    "MatemáticaMAT | 1 | Resuelve problemas de cantidadC1",
    "MatemáticaMAT | 2 | Resuelve problemas de formaC2",
  ]);
  await user.selectOptions(screen.getByLabelText("Filtrar por curso"), "1");
  expect(rows()).toHaveLength(2);
  expect(screen.getByText(/2 asociaciones/)).toBeTruthy();
  expect(calls.find((c) => c.path === "/api/cursos").query).toMatchObject({ size: "1000", sort: "nombre" });
});

test("Crea una asociación en el curso filtrado con el siguiente orden", async () => {
  const calls = backend({
    "POST /api/cursos-competencias": ({ body }) => reply(201, link(10, math, inquiry, body.orden)),
  });
  const user = userEvent.setup();
  renderTab();
  await screen.findByText(/3 asociaciones/);
  await user.selectOptions(screen.getByLabelText("Filtrar por curso"), "1");
  await user.click(screen.getByText("+ Nueva asociación"));
  const dialog = screen.getByRole("dialog", { name: "Nueva asociación" });
  expect(within(dialog).getByLabelText(/^Curso/).value).toBe("1");
  const competencySelect = within(dialog).getByLabelText(/^Competencia/);
  expect([...competencySelect.options].map((o) => o.textContent)).toEqual([
    "Seleccionar competencia",
    "Indaga con métodos científicos · C3",
  ]);
  expect(within(dialog).getByLabelText(/^Orden en el curso/).value).toBe("3");
  await user.selectOptions(competencySelect, "7");
  await user.click(within(dialog).getByRole("button", { name: "Asociar competencia" }));
  expect(
    await screen.findByText("Indaga con métodos científicos asociada a Matemática en el orden 3."),
  ).toBeTruthy();
  expect(calls.find((c) => c.method === "POST").body).toEqual({
    cursoId: 1,
    competenciaId: 7,
    orden: 3,
  });
});

test("Sin curso elegido la competencia espera y un curso completo lo explica", async () => {
  backend({
    "GET /api/cursos-competencias": page([
      link(1, math, quantity, 1),
      link(2, math, shape, 2),
      link(3, math, inquiry, 3),
    ]),
  });
  const user = userEvent.setup();
  renderTab();
  await screen.findByText(/3 asociaciones/);
  await user.click(screen.getByText("+ Nueva asociación"));
  const dialog = screen.getByRole("dialog");
  const competencySelect = within(dialog).getByLabelText(/^Competencia/);
  expect(competencySelect.disabled).toBe(true);
  expect(within(dialog).getByText("Primero elige el curso")).toBeTruthy();
  await user.selectOptions(within(dialog).getByLabelText(/^Curso/), "1");
  expect(within(dialog).getByText("El curso ya trabaja todas las competencias activas.")).toBeTruthy();
  expect(within(dialog).getByLabelText(/^Orden en el curso/).value).toBe("4");
  await user.selectOptions(within(dialog).getByLabelText(/^Curso/), "2");
  expect(within(dialog).getByLabelText(/^Orden en el curso/).value).toBe("1");
  await user.click(within(dialog).getByText("Cancelar"));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Edita el orden enviando la versión y muestra el rechazo del backend", async () => {
  const calls = backend({
    "PUT /api/cursos-competencias/:id": ({ body }) =>
      body.orden === 1
        ? reply(409, { message: "La relacion fue modificada; vuelva a cargarla" })
        : link(2, math, shape, body.orden),
  });
  const user = userEvent.setup();
  renderTab();
  await user.click(await screen.findByLabelText("Editar Resuelve problemas de forma en Matemática"));
  const dialog = screen.getByRole("dialog", { name: "Editar asociación" });
  const order = within(dialog).getByLabelText(/^Orden en el curso/);
  expect(order.value).toBe("2");
  expect(within(dialog).getByLabelText(/^Competencia/).value).toBe("6");
  await user.clear(order);
  await user.type(order, "1");
  await user.click(within(dialog).getByRole("button", { name: "Guardar cambios" }));
  expect((await within(dialog).findByRole("alert")).textContent).toBe(
    "La relacion fue modificada; vuelva a cargarla",
  );
  await user.clear(order);
  await user.type(order, "5");
  await user.click(within(dialog).getByRole("button", { name: "Guardar cambios" }));
  expect(await screen.findByText(/en el orden 5\./)).toBeTruthy();
  expect(calls.filter((c) => c.method === "PUT").at(-1)).toMatchObject({
    path: "/api/cursos-competencias/2",
    body: { cursoId: 1, competenciaId: 6, orden: 5, version: 4 },
  });
});

test("Quita una competencia del curso", async () => {
  const calls = backend({ "DELETE /api/cursos-competencias/:id": reply(204) });
  const user = userEvent.setup();
  renderTab();
  await user.click(await screen.findByLabelText("Más acciones para Indaga con métodos científicos en Ciencia"));
  await user.click(screen.getByRole("menuitem", { name: "Quitar del curso" }));
  const dialog = screen.getByRole("dialog", { name: "Quitar competencia del curso" });
  await user.click(within(dialog).getByRole("button", { name: "Quitar del curso" }));
  expect(
    await screen.findByText("Indaga con métodos científicos ya no está asociada a Ciencia."),
  ).toBeTruthy();
  expect(calls.find((c) => c.method === "DELETE").path).toBe("/api/cursos-competencias/3");
  await waitFor(() =>
    expect(calls.filter((c) => c.method === "GET" && c.path === "/api/cursos-competencias")).toHaveLength(2),
  );
});

test("Un error al quitar se muestra y Cancelar cierra la ventana", async () => {
  backend({ "DELETE /api/cursos-competencias/:id": reply(404, { message: "No existe la relacion" }) });
  const user = userEvent.setup();
  renderTab();
  await user.click(await screen.findByLabelText("Más acciones para Indaga con métodos científicos en Ciencia"));
  await user.click(screen.getByRole("menuitem", { name: "Quitar del curso" }));
  const dialog = screen.getByRole("dialog");
  await user.click(within(dialog).getByRole("button", { name: "Quitar del curso" }));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("No existe la relacion");
  await user.click(within(dialog).getByText("Cancelar"));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Solo lectura, vacíos y catálogo incompleto", async () => {
  backend({
    "GET /api/cursos-competencias": page([]),
    "GET /api/competencias": page([]),
  });
  const user = userEvent.setup();
  const { unmount } = renderTab();
  expect(await screen.findByText("Aún no hay competencias asociadas a los cursos.")).toBeTruthy();
  expect(screen.getByText("+ Nueva asociación").disabled).toBe(true);
  expect(screen.getByText(/registra primero al menos un curso y una competencia/)).toBeTruthy();
  await user.selectOptions(screen.getByLabelText("Filtrar por curso"), "2");
  expect(screen.getByText("Este curso todavía no tiene competencias asociadas.")).toBeTruthy();
  unmount();
  backend();
  renderTab(false);
  await screen.findByText(/3 asociaciones/);
  expect(screen.queryByText("+ Nueva asociación")).toBeNull();
  expect(screen.queryByText("Acciones")).toBeNull();
});

test("Un error al cargar se informa y Actualizar reintenta", async () => {
  let attempts = 0;
  backend({
    "GET /api/cursos-competencias": () =>
      ++attempts === 1 ? reply(500, { message: "No se pudo consultar" }) : page([]),
  });
  const user = userEvent.setup();
  renderTab();
  expect((await screen.findByRole("alert")).textContent).toBe("No se pudo consultar");
  await user.click(screen.getByText("Actualizar"));
  expect(await screen.findByText("Aún no hay competencias asociadas a los cursos.")).toBeTruthy();
});
