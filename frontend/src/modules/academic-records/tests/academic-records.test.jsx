import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
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
  persona: persona("Carmen Flores Vega", "40112233", "carmen@correo.pe"),
  alumnos: [
    { id: 1, alumnoId: 3, codigoAlumno: "EST-042", nombreCompleto: "Lucía Flores", parentesco: "MADRE", principal: true },
  ],
  activo: true,
};
const jorge = { id: 8, persona: persona("Jorge Rojas", "40998877"), alumnos: [], activo: false };
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
  expect(within(row).getByText(/Lucía Flores/).textContent).toBe("Lucía Flores · Madre");
  expect(within(row).getByText("Activo")).toBeTruthy();
  const inactive = screen.getByText("Jorge Rojas").closest("tr");
  expect(within(inactive).getByText("Sin estudiantes vinculados")).toBeTruthy();
  expect(within(inactive).getByText("Sin correo de contacto")).toBeTruthy();
  expect(screen.getByText("2 apoderados", { exact: false })).toBeTruthy();
  expect(screen.getByRole("button", { name: /Registrar apoderado/ }).disabled).toBe(true);
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
  expect(screen.getByRole("button", { name: /Vincular apoderado/ }).disabled).toBe(true);
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
  expect(screen.getByRole("button", { name: /Registrar docente/ }).disabled).toBe(true);
  expect(screen.queryByRole("button", { name: "Carga masiva" })).toBeNull();
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
