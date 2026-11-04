import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { Schedules } from "@/modules/schedules";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const YEAR = new Date().getFullYear();
const assignment = (overrides = {}) => ({
  id: 1,
  cursoId: 5,
  curso: "Matemática",
  seccionId: 8,
  seccion: "A",
  anioAcademico: YEAR,
  grado: 1,
  docenteId: 9,
  docente: "Patricia Morales",
  activo: true,
  version: 3,
  horariosActivos: 0,
  ...overrides,
});
const withSchedule = assignment({ id: 2, curso: "Ciencia", docente: "Luis Paredes", horariosActivos: 2 });
const retired = assignment({ id: 3, curso: "Arte", docente: "Ana Díaz", activo: false });
const teachers = [
  { id: 9, codigoDocente: "DOC-9", persona: { nombreCompleto: "Patricia Morales" } },
  { id: 12, codigoDocente: "DOC-12", persona: { nombreCompleto: "Carlos Vega" } },
];
const sections = [{ id: 8, nombre: "A", grado: 1, anioAcademico: YEAR }];
const courses = [{ id: 5, codigo: "MAT-1", nombre: "Matemática" }];

function backend(routes = {}) {
  return fakeBackend({
    "GET /api/asignaciones-cursos": page([assignment(), withSchedule]),
    "GET /api/docentes": page(teachers),
    "GET /api/secciones": page(sections),
    "GET /api/cursos": page(courses),
    ...routes,
  });
}

const renderPanel = (props = {}) =>
  render(<Schedules tab="asignaciones-docentes" canWrite {...props} />);
const rowOf = async (name) => (await screen.findByText(name, { selector: "strong" })).closest("tr");
async function chooseAction(user, name, curso, action) {
  await user.click(within(await rowOf(name)).getByLabelText(`Más acciones para ${name} en ${curso}`));
  await user.click(screen.getByRole("menuitem", { name: action }));
}

beforeEach(() => {
  signIn({ permissions: ["ACADEMICO_LEER", "ACADEMICO_ESCRIBIR"] });
  setUrl("/horarios/asignaciones-docentes");
});

test("Lista las asignaciones activas con su sección, horario y estado", async () => {
  const calls = backend();
  renderPanel();
  const first = await rowOf("Patricia Morales");
  expect(within(first).getByText("Matemática")).toBeTruthy();
  expect(within(first).getByText("1.° A")).toBeTruthy();
  expect(within(first).getByText("Sin bloques")).toBeTruthy();
  expect(within(first).getByText("Activa").className).toContain("status-success");
  expect(within(await rowOf("Luis Paredes")).getByText("2 bloques")).toBeTruthy();
  expect(await screen.findByText(/2 asignaciones/)).toBeTruthy();
  const list = calls.find((c) => c.path === "/api/asignaciones-cursos");
  expect(list.query).toMatchObject({ estado: "ACTIVAS", page: "0", size: "20" });
});

test("Filtra por estado y año, y las retiradas no tienen acciones", async () => {
  const user = userEvent.setup();
  const calls = backend({
    "GET /api/asignaciones-cursos": ({ query }) =>
      page(query.estado === "RETIRADAS" ? [retired] : [assignment()]),
  });
  renderPanel();
  await rowOf("Patricia Morales");
  await user.selectOptions(screen.getByLabelText("Filtrar por estado"), "RETIRADAS");
  const row = await rowOf("Ana Díaz");
  expect(within(row).getByText("Retirada")).toBeTruthy();
  expect(within(row).queryByLabelText(/Más acciones/)).toBeNull();
  await user.type(screen.getByLabelText("Filtrar por año académico"), "2026");
  await waitFor(() =>
    expect(calls.at(-1).query).toMatchObject({ estado: "RETIRADAS", anioAcademico: "2026" }),
  );
});

test("Crea una asignación eligiendo sección, curso del grado y docente", async () => {
  const user = userEvent.setup();
  const calls = backend({
    "POST /api/asignaciones-cursos": ({ body }) =>
      reply(201, assignment({ id: 7, docenteId: body.docenteId, docente: "Carlos Vega" })),
  });
  renderPanel();
  await user.click(await screen.findByRole("button", { name: "+ Nueva asignación" }));
  const dialog = await screen.findByRole("dialog");
  await user.click(within(dialog).getByRole("button", { name: "Asignar docente" }));
  expect(within(dialog).getByText("Seleccione la sección.")).toBeTruthy();

  await within(dialog).findByRole("option", { name: "1.° A" });
  await user.selectOptions(within(dialog).getByLabelText(/Sección/), "8");
  await within(dialog).findByRole("option", { name: "Matemática (MAT-1)" });
  await user.selectOptions(within(dialog).getByLabelText(/Curso/), "5");
  await user.selectOptions(within(dialog).getByLabelText(/Docente/), "12");
  await user.click(within(dialog).getByRole("button", { name: "Asignar docente" }));

  expect(await screen.findByText("Se asignó a Carlos Vega el curso Matemática (1.° A).")).toBeTruthy();
  expect(calls.find((c) => c.path === "/api/secciones").query).toMatchObject({
    activo: "true",
    anioAcademico: String(YEAR),
  });
  expect(calls.find((c) => c.path === "/api/cursos").query).toMatchObject({
    anio: String(YEAR),
    grado: "1",
  });
  expect(calls.find((c) => c.method === "POST").body).toEqual({ seccionId: 8, cursoId: 5, docenteId: 12 });
});

test("Muestra el rechazo del backend al crear una combinación repetida", async () => {
  const user = userEvent.setup();
  backend({
    "POST /api/asignaciones-cursos": reply(409, {
      code: "ASIGNACION_CURSO_DUPLICADA",
      message: "El curso ya tiene un docente asignado en esa sección",
    }),
  });
  renderPanel();
  await user.click(await screen.findByRole("button", { name: "+ Nueva asignación" }));
  const dialog = await screen.findByRole("dialog");
  await within(dialog).findByRole("option", { name: "1.° A" });
  await user.selectOptions(within(dialog).getByLabelText(/Sección/), "8");
  await within(dialog).findByRole("option", { name: "Matemática (MAT-1)" });
  await user.selectOptions(within(dialog).getByLabelText(/Curso/), "5");
  await user.selectOptions(within(dialog).getByLabelText(/Docente/), "9");
  await user.click(within(dialog).getByRole("button", { name: "Asignar docente" }));
  expect(await within(dialog).findByText("El curso ya tiene un docente asignado en esa sección")).toBeTruthy();
});

test("Edita la asignación enviando la versión", async () => {
  const user = userEvent.setup();
  const calls = backend({
    "PUT /api/asignaciones-cursos/:id": ({ body }) =>
      assignment({ docenteId: body.docenteId, docente: "Carlos Vega", version: 4 }),
  });
  renderPanel();
  await chooseAction(user, "Patricia Morales", "Matemática", "Editar");
  const dialog = await screen.findByRole("dialog");
  await within(dialog).findByRole("option", { name: "Carlos Vega (DOC-12)" });
  await within(dialog).findByRole("option", { name: "Matemática (MAT-1)" });
  await user.selectOptions(within(dialog).getByLabelText(/Docente/), "12");
  await user.click(within(dialog).getByRole("button", { name: "Guardar cambios" }));
  expect(await screen.findByText("Asignación de Carlos Vega actualizada.")).toBeTruthy();
  const put = calls.find((c) => c.method === "PUT");
  expect(put.path).toBe("/api/asignaciones-cursos/1");
  expect(put.body).toEqual({ seccionId: 8, cursoId: 5, docenteId: 12, version: 3 });
});

test("Retira al docente con motivo obligatorio", async () => {
  const user = userEvent.setup();
  const calls = backend({
    "PATCH /api/asignaciones-cursos/:id/retirar": () => assignment({ activo: false, version: 4 }),
  });
  renderPanel();
  await chooseAction(user, "Patricia Morales", "Matemática", "Retirar docente");
  const dialog = await screen.findByRole("dialog");
  await user.click(within(dialog).getByRole("button", { name: "Retirar docente" }));
  expect(within(dialog).getByText("Indique el motivo del retiro.")).toBeTruthy();
  expect(calls.some((c) => c.method === "PATCH")).toBe(false);

  await user.type(within(dialog).getByLabelText(/Motivo/), "  Licencia por maternidad ");
  await user.click(within(dialog).getByRole("button", { name: "Retirar docente" }));
  expect(await screen.findByText("Se retiró a Patricia Morales del curso Matemática.")).toBeTruthy();
  const patch = calls.find((c) => c.method === "PATCH");
  expect(patch.path).toBe("/api/asignaciones-cursos/1/retirar");
  expect(patch.body).toEqual({ motivo: "Licencia por maternidad", version: 3 });
});

test("Si tiene bloques de horario, avisa y lista los que debe reasignar", async () => {
  const user = userEvent.setup();
  backend({
    "PATCH /api/asignaciones-cursos/:id/retirar": reply(409, {
      code: "ASIGNACION_CON_HORARIOS_FUTUROS",
      message: "El docente tiene 2 bloque(s) de horario programados en este curso",
      details: [
        { horarioId: 31, diaSemana: "LUNES", horaInicio: "08:00:00", horaFin: "09:00:00", aulaId: 1, aula: "Aula 201" },
        { horarioId: 32, diaSemana: "MIERCOLES", horaInicio: "10:00:00", horaFin: "11:30:00", aulaId: 2, aula: "Lab. 01" },
      ],
    }),
  });
  renderPanel();
  await chooseAction(user, "Luis Paredes", "Ciencia", "Retirar docente");
  const dialog = await screen.findByRole("dialog");
  expect(within(dialog).getByText(/tiene 2 bloques de horario en este curso/)).toBeTruthy();
  await user.type(within(dialog).getByLabelText(/Motivo/), "Cambio de carga");
  await user.click(within(dialog).getByRole("button", { name: "Retirar docente" }));
  const pending = await within(dialog).findByRole("region", { name: "Bloques de horario pendientes" });
  expect(within(pending).getByText("Lunes 08:00 – 09:00 · Aula 201")).toBeTruthy();
  expect(within(pending).getByText("Miércoles 10:00 – 11:30 · Lab. 01")).toBeTruthy();
});

test("Sin permiso de escritura solo consulta", async () => {
  signIn({ permissions: ["ACADEMICO_LEER"] });
  backend();
  renderPanel({ canWrite: false });
  const row = await rowOf("Patricia Morales");
  expect(within(row).queryByLabelText(/Más acciones/)).toBeNull();
  expect(screen.queryByRole("button", { name: "+ Nueva asignación" })).toBeNull();
  expect(screen.queryByRole("columnheader", { name: "Acciones" })).toBeNull();
});
