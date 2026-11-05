import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { currentUrl, setUrl } from "../../../../tests/support/navigation.js";
import { Academic } from "@/modules/academic";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const course = (overrides = {}) => ({
  id: 1,
  codigo: "MAT",
  nombre: "Matemática",
  descripcion: "Razonamiento y resolución de problemas",
  horasSemanales: 6,
  activo: true,
  version: 2,
  gradoAcademicoId: 11,
  anioAcademico: 2027,
  grado: 1,
  ...overrides,
});
const grade = (id, numero, activo = true) => ({ id, numero, capacidadDefault: 30, activo, version: 0 });
const year = (id, anio, estado, grados) => ({
  id, anio, estado, fechaInicio: `${anio}-01-01`, fechaFin: `${anio}-12-31`, version: 0,
  admisionAbierta: false, periodos: [], grados, secciones: [],
});
/** 2026 cerrado no se ofrece; 2027 activo con 1.° y 2.° grado; 2028 en borrador sin grados. */
const YEARS = page([
  year(1, 2026, "CERRADO", [grade(5, 1)]),
  year(2, 2027, "ACTIVO", [grade(11, 1), grade(12, 2), grade(13, 3, false)]),
  year(3, 2028, "BORRADOR", []),
]);
const english = course({
  id: 2, codigo: "ING", nombre: "Inglés", descripcion: null, horasSemanales: 1,
  gradoAcademicoId: null, anioAcademico: null, grado: null,
});

const link = (id, cursoId, competenciaNombre, orden, activo = true) => ({
  id,
  cursoId,
  cursoCodigo: "MAT",
  cursoNombre: "Matemática",
  competenciaId: id * 10,
  competenciaCodigo: `C${id}`,
  competenciaNombre,
  orden,
  activo,
  version: 0,
});

function backend(routes = {}) {
  return fakeBackend({
    "GET /api/cursos": page([course(), english]),
    "GET /api/configuracion-academica/anios": YEARS,
    "GET /api/cursos-competencias": page([
      link(2, 1, "Resuelve problemas de cantidad", 2),
      link(1, 1, "Resuelve problemas de forma", 1),
      link(3, 1, "Competencia retirada", 3, false),
    ]),
    ...routes,
  });
}

const renderAcademic = (props = {}) => render(<Academic canWrite {...props} />);
const rowOf = async (name) => (await screen.findByText(name, { selector: "strong" })).closest("tr");

beforeEach(() => {
  signIn({ permissions: ["ACADEMICO_LEER", "ACADEMICO_ESCRIBIR"] });
  setUrl("/academico/cursos");
});

test("Lista los cursos con horas, estado y competencias activas", async () => {
  const calls = backend();
  renderAcademic();
  const math = await rowOf("Matemática");
  expect(within(math).getByText("MAT")).toBeTruthy();
  expect(within(math).getByText("6 h")).toBeTruthy();
  expect(within(math).getByText("Activo").className).toContain("status-active");
  expect(await within(math).findByText("2 competencias")).toBeTruthy();
  expect(within(await rowOf("Inglés")).getByText("0 competencias")).toBeTruthy();
  expect(screen.getByText(/2 cursos en el sistema · Última actualización: \d{2}:\d{2}/)).toBeTruthy();
  expect(calls.find((c) => c.path === "/api/cursos-competencias").query.size).toBe("1000");
});

test("El detalle muestra las competencias en orden y lleva a la edición", async () => {
  backend();
  const user = userEvent.setup();
  renderAcademic();
  await screen.findByText("2 competencias");
  await user.click(within(await rowOf("Matemática")).getByText("Ver detalle"));
  const dialog = screen.getByRole("dialog", { name: "Matemática" });
  expect(dialog.getAttribute("aria-describedby")).toBeTruthy();
  expect(within(dialog).getByText("Curso · MAT")).toBeTruthy();
  expect(within(dialog).getAllByRole("listitem").map((item) => item.querySelector("strong").textContent)).toEqual([
    "Resuelve problemas de forma",
    "Resuelve problemas de cantidad",
  ]);
  expect(within(dialog).getByText("Razonamiento y resolución de problemas")).toBeTruthy();
  await user.click(within(dialog).getByText("Editar curso"));
  expect(screen.getByRole("dialog", { name: "Editar curso" })).toBeTruthy();
});

test("Un curso sin descripción ni competencias lo indica en el detalle", async () => {
  backend();
  const user = userEvent.setup();
  renderAcademic();
  await user.click(within(await rowOf("Inglés")).getByText("Ver detalle"));
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByText("Sin descripción")).toBeTruthy();
  expect(within(dialog).getByText("Este curso aún no tiene competencias asociadas.")).toBeTruthy();
  await user.click(within(dialog).getByText("Cerrar", { selector: ".form-actions button" }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Crea un curso con los datos del formulario", async () => {
  const calls = backend({
    "POST /api/cursos": ({ body }) => reply(201, course({ id: 9, ...body, codigo: body.codigo.toUpperCase() })),
  });
  const user = userEvent.setup();
  renderAcademic();
  await rowOf("Matemática");
  await user.click(screen.getByText("+ Nuevo curso"));
  const dialog = screen.getByRole("dialog", { name: "Nuevo curso" });
  expect(within(dialog).getByText("Agrega un curso al plan curricular de un grado y año académico.")).toBeTruthy();
  const yearSelect = await within(dialog).findByLabelText(/^Año académico/);
  expect([...yearSelect.options].map((option) => option.textContent)).toEqual(["2027 · Activo", "2028 · Borrador"]);
  expect(yearSelect.value).toBe("2");
  const gradeSelect = within(dialog).getByLabelText(/^Grado/);
  expect([...gradeSelect.options].map((option) => option.textContent)).toEqual([
    "Selecciona el grado", "1° Grado", "2° Grado",
  ]);
  await user.type(within(dialog).getByLabelText(/^Código/), " art ");
  await user.type(within(dialog).getByLabelText(/^Nombre del curso/), " Arte y Cultura ");
  await user.type(within(dialog).getByLabelText(/^Horas semanales/), "2");
  await user.click(within(dialog).getByText("Crear curso"));
  // El grado es obligatorio: el formulario no se envía sin elegirlo.
  expect(gradeSelect.validity.valueMissing).toBe(true);
  expect(calls.some((c) => c.method === "POST")).toBe(false);
  await user.selectOptions(gradeSelect, "2° Grado");
  await user.click(within(dialog).getByText("Crear curso"));
  expect(await screen.findByText("Curso Arte y Cultura creado.")).toBeTruthy();
  expect(calls.find((c) => c.method === "POST").body).toEqual({
    codigo: "art",
    nombre: "Arte y Cultura",
    descripcion: null,
    horasSemanales: 2,
    gradoAcademicoId: 12,
  });
  await waitFor(() =>
    expect(calls.filter((c) => c.method === "GET" && c.path === "/api/cursos")).toHaveLength(2),
  );
});

test("Edita un curso enviando su versión y muestra los conflictos del backend", async () => {
  const calls = backend({
    "PUT /api/cursos/:id": ({ body }) =>
      body.nombre === "Repetido"
        ? reply(409, { message: "Ya existe un curso con ese codigo" })
        : course({ ...body, version: 3 }),
  });
  const user = userEvent.setup();
  renderAcademic();
  await user.click(await screen.findByLabelText("Editar Matemática"));
  const dialog = screen.getByRole("dialog", { name: "Editar curso" });
  const name = within(dialog).getByLabelText(/^Nombre del curso/);
  expect(name.value).toBe("Matemática");
  expect((await within(dialog).findByLabelText(/^Grado/)).value).toBe("11");
  await user.clear(name);
  await user.type(name, "Repetido");
  await user.click(within(dialog).getByText("Guardar cambios"));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("Ya existe un curso con ese codigo");
  await user.clear(name);
  await user.type(name, "Matemática I");
  const description = within(dialog).getByLabelText(/Descripción/);
  await user.clear(description);
  await user.click(within(dialog).getByText("Guardar cambios"));
  expect(await screen.findByText("Curso Matemática I actualizado.")).toBeTruthy();
  expect(calls.filter((c) => c.method === "PUT").at(-1).body).toEqual({
    codigo: "MAT",
    nombre: "Matemática I",
    descripcion: null,
    horasSemanales: 6,
    gradoAcademicoId: 11,
    version: 2,
  });
});

test("Muestra el año y grado de cada curso y avisa si un curso antiguo no lo tiene", async () => {
  backend();
  const user = userEvent.setup();
  renderAcademic();
  expect(within(await rowOf("Matemática")).getByText("1° Grado · 2027")).toBeTruthy();
  expect(within(await rowOf("Inglés")).getByText("Sin año ni grado")).toBeTruthy();
  await user.click(within(await rowOf("Inglés")).getByLabelText("Editar Inglés"));
  const dialog = screen.getByRole("dialog", { name: "Editar curso" });
  expect((await within(dialog).findByLabelText(/^Grado/)).value).toBe("");
});

test("Sin años abiertos no se puede elegir el contexto del curso", async () => {
  backend({ "GET /api/configuracion-academica/anios": page([year(1, 2026, "CERRADO", [grade(5, 1)])]) });
  const user = userEvent.setup();
  renderAcademic();
  await rowOf("Matemática");
  await user.click(screen.getByText("+ Nuevo curso"));
  expect(
    await within(screen.getByRole("dialog")).findByText(/No hay años académicos abiertos/),
  ).toBeTruthy();
});

test("Desactiva un curso desde el menú de acciones", async () => {
  const calls = backend({ "DELETE /api/cursos/:id": reply(204) });
  const user = userEvent.setup();
  renderAcademic();
  await user.click(await screen.findByLabelText("Más acciones para Inglés"));
  await user.click(screen.getByRole("menuitem", { name: "Desactivar" }));
  const dialog = screen.getByRole("dialog", { name: "Desactivar curso" });
  expect(within(dialog).getByText(/Inglés dejará de mostrarse/)).toBeTruthy();
  await user.click(within(dialog).getByRole("button", { name: "Desactivar curso" }));
  expect(await screen.findByText("Curso Inglés desactivado.")).toBeTruthy();
  expect(calls.find((c) => c.method === "DELETE").path).toBe("/api/cursos/2");
});

test("Un error al desactivar se muestra en la ventana y Cancelar la cierra", async () => {
  backend({ "DELETE /api/cursos/:id": reply(409, { message: "El curso tiene asignaciones activas" }) });
  const user = userEvent.setup();
  renderAcademic();
  await user.click(await screen.findByLabelText("Más acciones para Matemática"));
  await user.click(screen.getByRole("menuitem", { name: "Desactivar" }));
  const dialog = screen.getByRole("dialog");
  await user.click(within(dialog).getByRole("button", { name: "Desactivar curso" }));
  expect((await within(dialog).findByRole("alert")).textContent).toBe(
    "El curso tiene asignaciones activas",
  );
  await user.click(within(dialog).getByText("Cancelar"));
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(screen.queryByRole("alert")).toBeNull();
});

test("Sin permiso de escritura la pantalla es de solo consulta", async () => {
  backend();
  const user = userEvent.setup();
  renderAcademic({ canWrite: false });
  await user.click(within(await rowOf("Matemática")).getByText("Ver detalle"));
  expect(screen.queryByText("Editar curso")).toBeNull();
  expect(screen.queryByText("+ Nuevo curso")).toBeNull();
  expect(screen.queryByLabelText("Editar Matemática")).toBeNull();
  expect(screen.queryByLabelText("Más acciones para Matemática")).toBeNull();
});

test("Sin cursos invita a crear el primero y Actualizar vuelve a consultar", async () => {
  const calls = backend({ "GET /api/cursos": page([]) });
  const user = userEvent.setup();
  renderAcademic();
  expect(
    await screen.findByText("Aún no hay cursos registrados. Usa «Nuevo curso» para crear el primero."),
  ).toBeTruthy();
  await user.click(screen.getByText("Actualizar"));
  await waitFor(() => expect(calls.filter((c) => c.path === "/api/cursos")).toHaveLength(2));
  expect(calls.filter((c) => c.path === "/api/cursos-competencias")).toHaveLength(2);
});

test("Si fallan las competencias se muestran como no disponibles", async () => {
  backend({
    "GET /api/cursos-competencias": reply(500, null),
  });
  const user = userEvent.setup();
  renderAcademic();
  expect(await screen.findByText("No se pudieron consultar las competencias asociadas.")).toBeTruthy();
  const math = await rowOf("Matemática");
  expect(within(math).getByText("—")).toBeTruthy();
  await user.click(within(math).getByText("Ver detalle"));
  expect(screen.getByText("No se pudieron consultar las competencias.")).toBeTruthy();
});

test("Un error del listado se informa", async () => {
  backend({ "GET /api/cursos": reply(403, null) });
  renderAcademic();
  expect((await screen.findByRole("alert")).textContent).toBe(
    "No tienes permiso para realizar esta operación.",
  );
});

test("Las pestañas navegan por URL y las pendientes lo indican", async () => {
  backend();
  const user = userEvent.setup();
  const { rerender } = renderAcademic();
  await user.click(screen.getByRole("button", { name: "Reglas académicas" }));
  expect(currentUrl()).toBe("/academico/reglas-academicas");
  rerender(<Academic canWrite tab="reglas-academicas" />);
  expect(screen.getByText("Esta sección todavía está pendiente de implementación.")).toBeTruthy();
  expect(screen.getByRole("button", { name: "Reglas académicas" }).getAttribute("aria-current")).toBe("page");
  rerender(<Academic canWrite tab="desconocida" />);
  expect(currentUrl()).toBe("/academico/cursos");
});
