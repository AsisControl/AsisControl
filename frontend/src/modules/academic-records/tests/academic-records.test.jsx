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

test.each([
  ["apoderados", carmen, "Carmen", "Flores"],
  ["docentes", docente, "Rosa", "Pizarro"],
])("edita la ficha de %s y conserva la nacionalidad registrada", async (view, profile, nombres, apellidoPaterno) => {
  const root = `/api/${view}`;
  let detail = { ...profile, persona: { ...profile.persona, nombres, apellidoPaterno,
    fechaNacimiento: "1985-03-14", nacionalidadId: 9, sexo: "FEMENINO" } };
  const calls = fakeBackend({
    [`GET ${root}`]: () => page([detail]),
    [`GET ${root}/:id`]: () => detail,
    [`PUT ${root}/:id`]: ({ body }) => { detail = { ...detail, persona: { ...detail.persona, ...body.persona } }; return detail; },
  });
  setUrl(`${BASE}/${view}`);
  render(<RecordsScreen />);
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: /Ver ficha de/ }));
  await user.click(await screen.findByRole("button", { name: "Editar ficha" }));
  const address = screen.getByLabelText("Dirección");
  await user.type(address, "Av. Escuela 300");
  await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
  await screen.findByText("Ficha actualizada.");
  expect(calls.find(({ method }) => method === "PUT").body.persona)
    .toMatchObject({ direccion: "Av. Escuela 300", nacionalidadId: 9, nombres });
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
    ["Carmen Flores Vega", "Madre", "Sí", "Retirar"],
    ["Pedro Flores", "Tutor legal", "No", "Retirar"],
  ]);
  const missing = screen.getByText("Mario Díaz").closest("tr");
  expect(within(missing).getByText("Sin apoderado registrado")).toBeTruthy();
  expect(screen.getByRole("button", { name: "+ Vincular apoderado" }).disabled).toBe(false);
  expect(screen.queryByText("Próximamente")).toBeNull();
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

test.each([
  ["docentes", "docente", "DOCENTE", true],
  ["apoderados", "apoderado", "APODERADO", false],
])("Registra %s individualmente y solo entrega una temporal para una cuenta nueva", async (section, label, role, newAccount) => {
  setUrl(`${BASE}/${section}`);
  const endpoint = `/api/${section}`;
  const calls = fakeBackend({
    [`GET ${endpoint}`]: page([]),
    [`POST ${endpoint}`]: {
      id: 123,
      credencialAcceso: {
        row: 0, fullName: "Elena Cruz Rivera", role, code: "PERFIL001",
        username: "20260007", email: "elena@colegio.edu.pe", existing: !newAccount,
        temporaryPassword: newAccount ? "Temporal2026" : null, expiresAt: "2027-01-04T13:00:00Z",
      },
    },
  });
  const user = userEvent.setup();
  render(<RecordsScreen />);
  await user.click(await screen.findByRole("button", { name: new RegExp(`Registrar ${label}`) }));
  const dialog = await screen.findByRole("dialog", { name: `Registrar ${label}` });
  const form = within(dialog);
  for (const [field, value] of [
    [/^Nombres/, "Elena"], [/Apellido paterno/, "Cruz"], [/Apellido materno/, "Rivera"],
    [/Número de documento/, "41234123"], [/Fecha de nacimiento/, "1980-05-15"],
  ]) {
    fireEvent.change(form.getByLabelText(field), { target: { value } });
  }
  if (role === "DOCENTE") {
    fireEvent.change(form.getByLabelText(/Fecha de ingreso/), { target: { value: "2020-03-01" } });
    await user.selectOptions(form.getByLabelText(/Especialidad/), "MATEMATICA");
  }
  await user.click(form.getByRole("button", { name: "Guardar ficha" }));
  expect(await screen.findByText("Ficha registrada correctamente.")).toBeTruthy();
  const request = calls.find((call) => call.method === "POST");
  expect(request.body.persona.numeroDocumento).toBe("41234123");
  if (role === "DOCENTE") {
    expect(request.body).toMatchObject({ fechaIngreso: "2020-03-01", especialidad: "MATEMATICA" });
    const credential = await screen.findByRole("dialog", { name: "Credenciales iniciales" });
    expect(within(credential).getByText("Temporal2026")).toBeTruthy();
    await user.click(within(credential).getByRole("button", { name: "Entendido" }));
  } else {
    expect(request.body).not.toHaveProperty("alumnoIds");
  }
  expect(screen.queryByText("Temporal2026")).toBeNull();
});

test("Una ficha nueva exige documento y entrega la credencial del estudiante solo después de guardar", async () => {
  const calls = fakeBackend({
    "GET /api/alumnos": page([]),
    "POST /api/alumnos": {
      ...lucia,
      credencialAcceso: {
        row: 0, fullName: "Lucía Flores", role: "ALUMNO", code: "EST-042",
        username: "20260024", temporaryPassword: "Temporal2026", expiresAt: "2027-01-04T13:00:00Z",
      },
    },
  });
  const user = userEvent.setup();
  render(<RecordsScreen />);
  await user.click(await screen.findByRole("button", { name: /Registrar estudiante/ }));
  const form = within(screen.getByRole("region", { name: "Ficha del estudiante" }));
  const birthYear = new Date().getUTCFullYear() - 14;
  for (const [field, value] of [
    [/^Nombres/, "Lucía"], [/Primer apellido/, "Flores"], [/Fecha de nacimiento/, `${birthYear}-01-15`],
  ]) {
    fireEvent.change(form.getByLabelText(field), { target: { value } });
  }
  expect(form.getByLabelText(/Número de documento/).required).toBe(true);
  await user.click(form.getByRole("button", { name: "Guardar ficha" }));
  expect(calls.some((call) => call.method === "POST")).toBe(false);
  expect(screen.queryByRole("dialog", { name: "Credenciales iniciales" })).toBeNull();

  await user.selectOptions(form.getByLabelText(/Tipo de documento/), "PASAPORTE");
  fireEvent.change(form.getByLabelText(/Número de documento/), { target: { value: "AB123456" } });
  await user.click(form.getByRole("button", { name: "Guardar ficha" }));
  const credential = within(await screen.findByRole("dialog", { name: "Credenciales iniciales" }));
  expect(credential.getByText("20260024")).toBeTruthy();
  expect(credential.getByText("Temporal2026")).toBeTruthy();
  expect(calls.find((call) => call.method === "POST").body.persona).toMatchObject({
    tipoDocumento: "PASAPORTE", numeroDocumento: "AB123456",
  });
  await user.click(credential.getByRole("button", { name: "Entendido" }));
  expect(screen.queryByText("Temporal2026")).toBeNull();
});

test("El alta de apoderado conserva la ficha ante un conflicto de identidad y permite cancelar", async () => {
  setUrl(`${BASE}/apoderados`);
  const records = fakeBackend({
    "GET /api/apoderados": page([]),
    "POST /api/apoderados": reply(409, { message: "El documento ya está registrado con otros datos." }),
  });
  const user = userEvent.setup();
  render(<RecordsScreen />);
  const register = await screen.findByRole("button", { name: /Registrar apoderado/ });
  await user.click(register);
  let dialog = await screen.findByRole("dialog", { name: "Registrar apoderado" });
  let form = within(dialog);
  for (const [field, value] of [
    [/^Nombres/, "Elena"], [/Apellido paterno/, "Cruz"], [/Apellido materno/, "Rivera"],
    [/Número de documento/, "41234123"], [/Fecha de nacimiento/, "1980-05-15"],
  ]) {
    fireEvent.change(form.getByLabelText(field), { target: { value } });
  }
  await user.click(form.getByRole("button", { name: "Guardar ficha" }));
  expect((await form.findByRole("alert")).textContent).toBe("El documento ya está registrado con otros datos.");
  expect(form.getByLabelText(/Número de documento/).value).toBe("41234123");
  expect(screen.queryByRole("dialog", { name: "Credenciales iniciales" })).toBeNull();
  await user.click(form.getByRole("button", { name: "Cancelar" }));
  expect(screen.queryByRole("dialog")).toBeNull();

  await user.click(register);
  dialog = await screen.findByRole("dialog", { name: "Registrar apoderado" });
  form = within(dialog);
  expect(form.getByLabelText(/Número de documento/).value).toBe("");
  expect(form.queryByRole("alert")).toBeNull();
  await user.click(form.getByRole("button", { name: "Cerrar ventana" }));
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(records.filter((call) => call.method === "POST")).toHaveLength(1);
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
