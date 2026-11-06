import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { currentUrl, setUrl } from "../../../../tests/support/navigation.js";
import { Schedules } from "@/modules/schedules";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const fields = (values) =>
  Object.entries(values).map(([campo, [anterior, nuevo]]) => ({ campo, anterior, nuevo }));
const entry = (overrides = {}) => ({
  id: 1,
  fecha: "2026-03-02T15:30:00Z",
  actorIdentificador: "admin",
  actorNombre: "Ana Rojas",
  rolActor: "ADMINISTRADOR",
  accion: "ACTUALIZAR_HORARIO",
  entidad: "Horario",
  registroId: "7",
  motivo: null,
  campos: fields({
    curso: ["Matemática", "Matemática"],
    seccion: ["1° A", "1° A"],
    aula: ["Aula 201", "Lab. 01"],
    dia: ["LUNES", "MIERCOLES"],
    horaInicio: ["08:00", "08:00"],
  }),
  ...overrides,
});
const created = entry({
  id: 2,
  accion: "CREAR_HORARIO",
  actorNombre: null,
  actorIdentificador: "usuario.inactivo",
  rolActor: null,
  campos: fields({ curso: [null, "Arte"], seccion: [null, "2° B"], horaFin: [null, "09:30"] }),
});
const retired = entry({
  id: 3,
  accion: "RETIRAR_ASIGNACION_CURSO",
  entidad: "AsignacionCurso",
  motivo: "Licencia del docente",
  campos: fields({ curso: ["Arte", "Arte"], activo: ["true", "false"] }),
});
const unchanged = entry({
  id: 4,
  accion: "ACCION_NUEVA",
  campos: fields({ dia: ["LUNES", "LUNES"] }),
});
const sections = [
  { id: 8, nombre: "A", grado: 1, anioAcademico: 2026, capacidadMaxima: 30, activo: true },
  { id: 9, nombre: "B", grado: 2, anioAcademico: 2027, capacidadMaxima: 30, activo: true },
];

function backend(routes = {}) {
  return fakeBackend({
    "GET /api/horarios/historial": page([entry(), created, retired, unchanged]),
    "GET /api/secciones": page(sections),
    "GET /api/docentes": page([
      { id: 9, codigoDocente: "DOC-9", persona: { nombreCompleto: "Patricia Morales" } },
    ]),
    "GET /api/aulas": page([{ id: 3, nombre: "Aula 201" }]),
    ...routes,
  });
}

const renderHistory = (props = {}) =>
  render(<Schedules canWrite canViewHistory tab="historial-de-cambios" {...props} />);
const rowOf = async (text) => (await screen.findByText(text)).closest("tr");
const historyCalls = (calls) => calls.filter((call) => call.path === "/api/horarios/historial");

beforeEach(() => {
  signIn({ permissions: ["ACADEMICO_LEER", "AUDITORIA_LEER"] });
  setUrl("/horarios/historial-de-cambios");
});

test("Muestra quién hizo cada cambio, cuándo y los valores anterior y nuevo", async () => {
  backend();
  renderHistory();
  const edited = await rowOf("Bloque editado");
  expect(within(edited).getByText("Ana Rojas")).toBeTruthy();
  expect(within(edited).getByText("Administrador")).toBeTruthy();
  expect(within(edited).getByText("Bloque editado").className).toContain("status-info");
  expect(within(edited).getByText("Matemática · 1° A")).toBeTruthy();
  expect(within(edited).getByText(/10:30:00/)).toBeTruthy();
  expect(within(edited).getByText("Aula:").closest("li").textContent).toBe("Aula: Aula 201 → Lab. 01");
  expect(within(edited).getByText("Día:").closest("li").textContent).toBe("Día: Lunes → Miércoles");
  expect(within(edited).queryByText("Hora de inicio:")).toBeNull();
  expect(screen.getByText("4 cambios", { exact: false })).toBeTruthy();
});

test("Un alta no tiene valor anterior y el actor inactivo se muestra por su usuario", async () => {
  backend();
  renderHistory();
  const row = await rowOf("usuario.inactivo");
  expect(within(row).getByText("Bloque creado").className).toContain("status-success");
  expect(within(row).getByText("Arte · 2° B")).toBeTruthy();
  expect(within(row).getByText("Hora de fin:").closest("li").textContent).toBe("Hora de fin: — → 09:30");
});

test("Muestra el motivo y el estado de un retiro de docente", async () => {
  backend();
  renderHistory();
  const row = await rowOf("Licencia del docente");
  expect(within(row).getByText("Docente retirado").className).toContain("status-warning");
  expect(within(row).getByText("Estado:").closest("li").textContent).toBe("Estado: Activa → Retirada");
});

test("Una edición sin diferencias lo indica y una acción nueva usa su código", async () => {
  backend();
  renderHistory();
  const row = await rowOf("Accion nueva");
  expect(within(row).getByText("Sin cambios en los datos")).toBeTruthy();
});

test("Envía cada filtro al servidor y vuelve a la primera página", async () => {
  const calls = backend({
    "GET /api/horarios/historial": { ...page([entry()], 2), totalElements: 21 },
  });
  const user = userEvent.setup();
  renderHistory();
  await user.click(await screen.findByText("Siguiente"));
  await waitFor(() => expect(historyCalls(calls).at(-1).query.page).toBe("1"));

  await user.selectOptions(await screen.findByLabelText("Filtrar por aula"), "3");
  await waitFor(() => expect(historyCalls(calls).at(-1).query).toMatchObject({ aulaId: "3", page: "0" }));
  await user.selectOptions(screen.getByLabelText("Filtrar por docente"), "9");
  await user.selectOptions(screen.getByLabelText("Filtrar por sección"), "8");
  await user.type(screen.getByLabelText("Desde la fecha"), "2026-03-01");
  await user.type(screen.getByLabelText("Hasta la fecha"), "2026-03-31");
  await waitFor(() =>
    expect(historyCalls(calls).at(-1).query).toMatchObject({
      aulaId: "3",
      docenteId: "9",
      seccionId: "8",
      desde: "2026-03-01",
      hasta: "2026-03-31",
    }),
  );
});

test("Al elegir el año solo ofrece sus secciones y limpia la sección elegida", async () => {
  const calls = backend();
  const user = userEvent.setup();
  renderHistory();
  const sectionFilter = await screen.findByLabelText("Filtrar por sección");
  await within(sectionFilter).findByText("2.° B · 2027");
  await user.selectOptions(sectionFilter, "9");
  await user.type(screen.getByLabelText("Filtrar por año académico"), "2026");
  await waitFor(() => expect(historyCalls(calls).at(-1).query.anioAcademico).toBe("2026"));
  expect(historyCalls(calls).at(-1).query.seccionId).toBeUndefined();
  expect(within(sectionFilter).queryByText("2.° B · 2027")).toBeNull();
  expect(within(sectionFilter).getByText("1.° A · 2026")).toBeTruthy();
});

test("Incluye las aulas inhabilitadas entre las opciones del filtro", async () => {
  const calls = backend();
  renderHistory();
  await screen.findByText("Bloque editado");
  await waitFor(() =>
    expect(calls.find((call) => call.path === "/api/aulas")?.query.estado).toBe("TODAS"),
  );
});

test("Avisa si no hay cambios con los filtros y permite actualizar", async () => {
  const calls = backend({ "GET /api/horarios/historial": page([]) });
  const user = userEvent.setup();
  renderHistory();
  expect(await screen.findByText("No hay cambios registrados con estos filtros.")).toBeTruthy();
  await user.click(screen.getByText("Actualizar"));
  await waitFor(() => expect(historyCalls(calls).length).toBeGreaterThan(1));
});

test("Muestra el error del servidor, como un rango de fechas inválido", async () => {
  backend({
    "GET /api/horarios/historial": reply(422, {
      message: "La fecha final debe ser igual o posterior a la inicial",
    }),
  });
  renderHistory();
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect(screen.getByRole("alert").textContent).toContain("fecha final");
  expect(screen.queryByText("No hay cambios registrados con estos filtros.")).toBeNull();
});

test("Si fallan las opciones de los filtros, el historial sigue visible con el aviso", async () => {
  backend({ "GET /api/docentes": reply(403, { message: "Sin permiso para listar docentes" }) });
  renderHistory();
  expect(await screen.findByText("Bloque editado")).toBeTruthy();
  expect((await screen.findByRole("alert")).textContent).toContain("Sin permiso");
});

test("Sin permiso de auditoría no aparece la pestaña y la URL vuelve al horario", async () => {
  backend({
    "GET /api/horarios": page([]),
    "GET /api/asignaciones-cursos": page([]),
  });
  render(<Schedules canWrite tab="historial-de-cambios" />);
  await waitFor(() => expect(currentUrl()).toBe("/horarios/horario"));
  expect(screen.queryByText("Historial de cambios")).toBeNull();
});
