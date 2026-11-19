import { beforeEach, expect, test, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { usePathname } from "next/navigation";
import { currentUrl, setUrl } from "../../../../tests/support/navigation.js";
import { AcademicRecords } from "@/modules/academic-records";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const BASE = "/matricula/fichas";
/** Como Enrollments: la vista sale de /matricula/fichas/{vista}. */
function RecordsScreen() {
  return <AcademicRecords basePath={BASE} view={usePathname().split("/")[3]} />;
}
const persona = (nombreCompleto, numeroDocumento, correo = null) => ({
  id: Number(numeroDocumento.slice(-3)),
  nombreCompleto,
  tipoDocumento: "DNI",
  numeroDocumento,
  correo,
});
const carmen = {
  id: 7,
  codigoApoderado: "APO20260007",
  persona: persona("Carmen Flores Vega", "40112233", "carmen@correo.pe"),
  alumnos: [
    { id: 1, alumnoId: 3, codigoAlumno: "EST-042", nombreCompleto: "Lucía Flores", parentesco: "MADRE", principal: true },
  ],
  activo: true,
};
const jorge = { id: 8, codigoApoderado: null, persona: persona("Jorge Rojas", "40998877"), alumnos: [], activo: false };
const lucia = {
  id: 3,
  codigoAlumno: "EST-042",
  persona: persona("Lucía Flores", "71234567"),
  activo: true,
  apoderados: [
    { id: 1, apoderadoId: 7, nombreCompleto: "Carmen Flores Vega", parentesco: "MADRE", principal: true },
    { id: 2, apoderadoId: 9, nombreCompleto: "Pedro Flores", parentesco: "TUTOR_LEGAL", principal: false },
  ],
};
const mario = { id: 4, codigoAlumno: "EST-043", persona: persona("Mario Díaz", "71000111"), activo: true, apoderados: [] };
const docente = {
  id: 11,
  codigoDocente: "DOC-001",
  fechaIngreso: "2020-03-01",
  especialidad: "CIENCIA_Y_TECNOLOGIA",
  persona: persona("Rosa Pizarro", "41222333", "rosa@colegio.pe"),
  activo: true,
};

beforeEach(() => {
  signIn();
  setUrl(BASE);
});

test("Agrupa las vistas y cambia la URL al elegir una", async () => {
  fakeBackend({ "GET /api/alumnos": page([]) });
  const user = userEvent.setup();
  render(<RecordsScreen />);
  const families = screen.getByRole("group", { name: "Estudiantes y familias" });
  expect(within(families).getAllByRole("button").map((b) => b.textContent)).toEqual([
    "Estudiantes",
    "Apoderados",
    "Parentescos",
  ]);
  expect(within(screen.getByRole("group", { name: "Personal" })).getByText("Docentes")).toBeTruthy();
  expect(within(families).getByText("Estudiantes").getAttribute("aria-current")).toBe("page");
  expect(await screen.findByText("Todavía no hay estudiantes registrados.")).toBeTruthy();
  await user.click(screen.getByText("Docentes"));
  expect(currentUrl()).toBe(`${BASE}/docentes`);
});

test("Una vista desconocida vuelve a la vista de estudiantes", async () => {
  setUrl(`${BASE}/otra`);
  render(<RecordsScreen />);
  await waitFor(() => expect(currentUrl()).toBe(BASE));
});

test("Solo el administrador ve las fichas académicas", () => {
  signIn({ role: "DOCENTE" });
  render(<RecordsScreen />);
  expect(screen.getByText("No tienes permiso para gestionar las fichas académicas.")).toBeTruthy();
});

test("Apoderados lista a sus estudiantes a cargo y busca en el backend", async () => {
  const calls = fakeBackend({ "GET /api/apoderados": page([carmen, jorge]) });
  setUrl(`${BASE}/apoderados`);
  const user = userEvent.setup();
  render(<RecordsScreen />);
  const row = (await screen.findByText("Carmen Flores Vega")).closest("tr");
  expect(within(row).getByText("DNI 40112233")).toBeTruthy();
  expect(within(row).getByText("APO20260007")).toBeTruthy();
  expect(within(row).getByText(/Lucía Flores/).textContent).toBe("Lucía Flores · Madre");
  expect(within(row).getByText("Activo")).toBeTruthy();
  const inactive = screen.getByText("Jorge Rojas").closest("tr");
  expect(within(inactive).getByText("Sin estudiantes vinculados")).toBeTruthy();
  expect(inactive.cells[0].textContent).toBe("—");
  expect(within(inactive).getByText("Sin correo de contacto")).toBeTruthy();
  expect(screen.getByText("2 apoderados", { exact: false })).toBeTruthy();
  expect(screen.getByRole("button", { name: /Registrar apoderado/ }).disabled).toBe(false);
  await user.type(screen.getByLabelText("Buscar por nombre o documento"), "40112233");
  await user.click(screen.getByText("Buscar"));
  await waitFor(() => expect(calls.at(-1).query.search).toBe("40112233"));
});

test("La ficha del apoderado consulta sus datos y sus estudiantes a cargo", async () => {
  const calls = fakeBackend({
    "GET /api/apoderados": page([carmen]),
    "GET /api/apoderados/:id": {
      ...carmen,
      persona: {
        ...carmen.persona,
        nombres: "Carmen",
        apellidoPaterno: "Flores",
        apellidoMaterno: "Vega",
        sexo: "FEMENINO",
        fechaNacimiento: "1985-03-14",
        nacionalidad: "Peruana",
        telefono: "999888777",
        direccion: null,
      },
    },
  });
  setUrl(`${BASE}/apoderados`);
  const user = userEvent.setup();
  render(<RecordsScreen />);
  await user.click(await screen.findByRole("button", { name: "Ver ficha de Carmen Flores Vega" }));
  const dialog = screen.getByRole("dialog");
  expect(await within(dialog).findByText("14/03/1985")).toBeTruthy();
  expect(calls.at(-1).path).toBe("/api/apoderados/7");
  expect(within(dialog).getByText("DNI 40112233")).toBeTruthy();
  expect(within(dialog).getByText("APO20260007")).toBeTruthy();
  expect(within(dialog).getByText("Femenino")).toBeTruthy();
  expect(within(dialog).getByText("Dirección").nextSibling.textContent).toBe("—");
  const link = within(dialog).getByText("EST-042").closest("tr");
  expect([...link.cells].map((cell) => cell.textContent)).toEqual(["EST-042", "Lucía Flores", "Madre", "Sí"]);
  await user.click(within(dialog).getByText("Cerrar"));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Si la ficha no se puede consultar muestra el error", async () => {
  fakeBackend({
    "GET /api/docentes": page([docente]),
    "GET /api/docentes/:id": reply(404, { message: "No se encontro el docente" }),
  });
  setUrl(`${BASE}/docentes`);
  const user = userEvent.setup();
  render(<RecordsScreen />);
  await user.click(await screen.findByRole("button", { name: "Ver ficha de Rosa Pizarro" }));
  expect(await within(screen.getByRole("dialog")).findByText("No se encontro el docente")).toBeTruthy();
});

test("Parentescos muestra una fila por vínculo y avisa si falta el apoderado", async () => {
  fakeBackend({ "GET /api/alumnos": page([lucia, mario]) });
  setUrl(`${BASE}/parentescos`);
  render(<RecordsScreen />);
  const rows = (await screen.findAllByText("Lucía Flores")).map((name) => name.closest("tr"));
  expect(rows.map((row) => [...row.cells].slice(1).map((cell) => cell.textContent))).toEqual([
    ["Carmen Flores Vega", "Madre", "Sí"],
    ["Pedro Flores", "Tutor legal", "No"],
  ]);
  const missing = screen.getByText("Mario Díaz").closest("tr");
  expect(within(missing).getByText("Sin apoderado registrado")).toBeTruthy();
  expect(screen.getByRole("button", { name: "+ Vincular apoderado" }).disabled).toBe(false);
});

test("La carga masiva se abre desde apoderados y parentescos", async () => {
  fakeBackend({ "GET /api/apoderados": page([]) });
  setUrl(`${BASE}/apoderados`);
  const user = userEvent.setup();
  render(<RecordsScreen />);
  expect(await screen.findByText("Todavía no hay apoderados registrados.")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Carga masiva" }));
  expect(
    within(screen.getByRole("dialog")).getByText("Carga masiva de estudiantes y familias"),
  ).toBeTruthy();
});

test("Docentes muestra código, especialidad y estado", async () => {
  fakeBackend({ "GET /api/docentes": page([docente]) });
  setUrl(`${BASE}/docentes`);
  render(<RecordsScreen />);
  const row = (await screen.findByText("Rosa Pizarro")).closest("tr");
  expect(within(row).getByText("DOC-001")).toBeTruthy();
  expect(within(row).getByText("41222333")).toBeTruthy();
  expect(within(row).getByText("Ciencia y Tecnología")).toBeTruthy();
  expect(screen.getByRole("button", { name: /Registrar docente/ }).disabled).toBe(false);
});

test("La ficha del docente muestra código, especialidad e ingreso", async () => {
  fakeBackend({
    "GET /api/docentes": page([docente]),
    "GET /api/docentes/:id": { ...docente, persona: { ...docente.persona, nombres: "Rosa", apellidoPaterno: "Pizarro", fechaNacimiento: "1980-01-02" } },
  });
  setUrl(`${BASE}/docentes`);
  const user = userEvent.setup();
  render(<RecordsScreen />);
  await user.click(await screen.findByRole("button", { name: "Ver ficha de Rosa Pizarro" }));
  const dialog = screen.getByRole("dialog");
  expect(await within(dialog).findByText("01/03/2020")).toBeTruthy();
  expect(within(dialog).getByText("Código").nextSibling.textContent).toBe("DOC-001");
  expect(within(dialog).getByText("Ciencia y Tecnología")).toBeTruthy();
  expect(within(dialog).getByText("rosa@colegio.pe")).toBeTruthy();
});

test("Si el listado falla se puede reintentar", async () => {
  let attempts = 0;
  fakeBackend({
    "GET /api/docentes": () => (++attempts === 1 ? reply(500, null) : page([docente])),
  });
  setUrl(`${BASE}/docentes`);
  const user = userEvent.setup();
  render(<RecordsScreen />);
  await user.click(await screen.findByText("Reintentar carga"));
  expect(await screen.findByText("Rosa Pizarro")).toBeTruthy();
});

async function fillPerson(user, dialog) {
  await user.type(within(dialog).getByLabelText(/^Nombres/), "Elena");
  await user.type(within(dialog).getByLabelText(/^Apellido paterno/), "Quispe");
  await user.type(within(dialog).getByLabelText(/^Apellido materno/), "Rojas");
  await user.type(within(dialog).getByLabelText(/^Número de documento/), "45678901");
  fireEvent.change(within(dialog).getByLabelText(/^Fecha de nacimiento/), { target: { value: "1985-04-12" } });
}

test("Registra un apoderado, muestra su código y actualiza el listado", async () => {
  const calls = fakeBackend({
    "GET /api/apoderados": page([carmen]),
    "POST /api/apoderados": () => ({ ...carmen, id: 20, codigoApoderado: "APO20260020", persona: persona("Elena Quispe Rojas", "45678901"), alumnos: [] }),
  });
  setUrl(`${BASE}/apoderados`);
  const user = userEvent.setup();
  render(<RecordsScreen />);
  await screen.findByText("Carmen Flores Vega");
  await user.click(screen.getByRole("button", { name: /Registrar apoderado/ }));
  const dialog = screen.getByRole("dialog");
  await fillPerson(user, dialog);
  await user.click(within(dialog).getByRole("button", { name: "Registrar apoderado" }));
  expect(await screen.findByText("APO20260020")).toBeTruthy();
  const sent = calls.find((call) => call.method === "POST").body;
  expect(sent.persona).toMatchObject({
    nombres: "Elena", apellidoPaterno: "Quispe", apellidoMaterno: "Rojas",
    tipoDocumento: "DNI", numeroDocumento: "45678901", fechaNacimiento: "1985-04-12",
  });
  expect(calls.filter((call) => call.method === "GET" && call.path === "/api/apoderados").length).toBeGreaterThan(1);
});

test("Muestra el error del backend al registrar un apoderado sin cerrar el formulario", async () => {
  fakeBackend({
    "GET /api/apoderados": page([]),
    "POST /api/apoderados": reply(409, { message: "Ya existe una persona con ese documento" }),
  });
  setUrl(`${BASE}/apoderados`);
  const user = userEvent.setup();
  render(<RecordsScreen />);
  await screen.findByText("Todavía no hay apoderados registrados.");
  await user.click(screen.getByRole("button", { name: /Registrar apoderado/ }));
  const dialog = screen.getByRole("dialog");
  await fillPerson(user, dialog);
  await user.click(within(dialog).getByRole("button", { name: "Registrar apoderado" }));
  expect(await within(dialog).findByText("Ya existe una persona con ese documento")).toBeTruthy();
});

test("Registra un docente con su fecha de ingreso y especialidad", async () => {
  const calls = fakeBackend({
    "GET /api/docentes": page([docente]),
    "POST /api/docentes": { ...docente, id: 30, codigoDocente: "DOC20260030", persona: persona("Elena Quispe Rojas", "45678901") },
  });
  setUrl(`${BASE}/docentes`);
  const user = userEvent.setup();
  render(<RecordsScreen />);
  await screen.findByText("Rosa Pizarro");
  await user.click(screen.getByRole("button", { name: /Registrar docente/ }));
  const dialog = screen.getByRole("dialog");
  await fillPerson(user, dialog);
  fireEvent.change(within(dialog).getByLabelText(/^Fecha de ingreso/), { target: { value: "2026-03-01" } });
  await user.selectOptions(within(dialog).getByLabelText(/^Especialidad/), "INGLES");
  await user.click(within(dialog).getByRole("button", { name: "Registrar docente" }));
  expect(await screen.findByText("DOC20260030")).toBeTruthy();
  const sent = calls.find((call) => call.method === "POST").body;
  expect(sent).toMatchObject({ fechaIngreso: "2026-03-01", especialidad: "INGLES" });
  expect(sent.persona.nombres).toBe("Elena");
});

test("Vincula un apoderado a un estudiante con su parentesco", async () => {
  const calls = fakeBackend({
    "GET /api/alumnos": page([lucia, mario]),
    "GET /api/apoderados": page([carmen]),
    "POST /api/alumnos/:id/apoderados": { id: 5, apoderadoId: 7, nombreCompleto: "Carmen Flores Vega", parentesco: "PADRE", principal: true },
  });
  setUrl(`${BASE}/parentescos`);
  const user = userEvent.setup();
  render(<RecordsScreen />);
  await screen.findByText("Mario Díaz");
  await user.click(screen.getByRole("button", { name: "+ Vincular apoderado" }));
  const dialog = screen.getByRole("dialog");
  await user.type(within(dialog).getByLabelText(/^Estudiante/), "Mario");
  await user.click(within(dialog).getByRole("button", { name: "Buscar estudiante" }));
  await user.click(await within(dialog).findByRole("button", { name: "Mario Díaz · EST-043" }));
  await user.type(within(dialog).getByLabelText(/^Apoderado/), "Carmen");
  await user.click(within(dialog).getByRole("button", { name: "Buscar apoderado" }));
  await user.click(await within(dialog).findByRole("button", { name: /Carmen Flores Vega/ }));
  await user.selectOptions(within(dialog).getByLabelText(/^Parentesco/), "PADRE");
  await user.click(within(dialog).getByLabelText(/apoderado principal/));
  await user.click(within(dialog).getByRole("button", { name: "Vincular" }));
  await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  expect(calls.find((call) => call.method === "POST")).toMatchObject({
    path: "/api/alumnos/4/apoderados",
    body: { apoderadoId: 7, parentesco: "PADRE", principal: true },
  });
});

test("Desde la fila de un estudiante sin apoderado el estudiante ya viene elegido", async () => {
  fakeBackend({ "GET /api/alumnos": page([lucia, mario]) });
  setUrl(`${BASE}/parentescos`);
  const user = userEvent.setup();
  render(<RecordsScreen />);
  await user.click(await screen.findByRole("button", { name: "Vincular apoderado a Mario Díaz" }));
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByText("Mario Díaz · EST-043")).toBeTruthy();
});

test("No vincula sin elegir al estudiante y al apoderado", async () => {
  const calls = fakeBackend({ "GET /api/alumnos": page([]) });
  setUrl(`${BASE}/parentescos`);
  const user = userEvent.setup();
  render(<RecordsScreen />);
  await screen.findByText("Todavía no hay estudiantes registrados.");
  await user.click(screen.getByRole("button", { name: "+ Vincular apoderado" }));
  await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Vincular" }));
  expect(await screen.findByText("Elige al estudiante y al apoderado para vincularlos.")).toBeTruthy();
  expect(calls.some((call) => call.method === "POST")).toBe(false);
});
