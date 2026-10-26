import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { currentUrl, setUrl } from "../../../../tests/support/navigation.js";
import { Administration } from "@/modules/administration";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const roles = [
  { id: 1, nombre: "ADMINISTRADOR", descripcion: "", permisos: [] },
  { id: 2, nombre: "DOCENTE", descripcion: "", permisos: [] },
  { id: 3, nombre: "ALUMNO", descripcion: "", permisos: [] },
  { id: 4, nombre: "APODERADO", descripcion: "", permisos: [] },
  { id: 5, nombre: "TESORERIA", descripcion: "Caja", permisos: [] },
];

const people = [
  {
    id: 70,
    nombreCompleto: "Patricia Morales",
    numeroDocumento: "40404040",
    perfiles: ["ALUMNO"],
    rolesConAcceso: ["ADMINISTRADOR"],
  },
  {
    id: 71,
    nombreCompleto: "Rosa Quispe",
    numeroDocumento: "50505050",
    perfiles: [],
    rolesConAcceso: [],
  },
];

const student = (id, nombreCompleto) => ({
  id,
  codigoAlumno: `EST-${id}`,
  activo: true,
  apoderados: [],
  persona: { id: id + 100, nombreCompleto },
});

const created = {
  id: 90,
  nombreCompleto: "Patricia Morales",
  rol: "DOCENTE",
  username: "pmorales",
  correo: "pmorales@colegio.edu.pe",
};

function backend(overrides = {}) {
  return fakeBackend({
    "GET /api/roles": roles,
    "GET /api/usuarios/personas": page(people),
    "GET /api/alumnos": page([student(1, "Luis García"), student(2, "Mía García")]),
    "POST /api/usuarios/provisionar": created,
    ...overrides,
  });
}

const renderForm = () =>
  render(<Administration currentUserId="1" tab="usuarios" view="nuevo" />);

const summary = () => screen.getByRole("complementary");

async function fillAccess(user, password = "Temporal2026") {
  await user.type(screen.getByLabelText(/^Usuario/), "pmorales");
  await user.type(screen.getByLabelText(/^Correo del acceso/), "pmorales@colegio.edu.pe");
  await user.type(screen.getByLabelText(/^Contraseña temporal/), password);
}

beforeEach(() => {
  signIn();
  setUrl("/administracion/usuarios/nuevo");
});

test("Crea un acceso docente para una persona registrada sin ese perfil", async () => {
  const calls = backend();
  const user = userEvent.setup();
  renderForm();
  expect(within(summary()).getByText(/Falta: persona, rol, usuario, correo, contraseña/)).toBeTruthy();
  await user.click(await screen.findByText("Patricia Morales"));
  expect(within(summary()).getByText("Patricia Morales")).toBeTruthy();
  const teacher = await screen.findByRole("radio", { name: /Docente/ });
  expect(screen.getByRole("radio", { name: /Administrador/ }).disabled).toBe(true);
  expect(screen.queryByRole("radio", { name: /TESORERIA/ })).toBeNull();
  await user.click(teacher);
  expect(within(summary()).getByText("Se creará el perfil de docente")).toBeTruthy();
  await user.type(screen.getByLabelText(/Código docente/), "DOC-2026-001");
  await user.type(screen.getByLabelText(/Fecha de ingreso/), "2026-03-01");
  await user.selectOptions(screen.getByLabelText(/Especialidad/), "CIENCIA_Y_TECNOLOGIA");
  await fillAccess(user);
  await user.click(screen.getByRole("checkbox"));
  await user.click(screen.getByText("Crear usuario"));
  const success = await screen.findByRole("status");
  expect(within(success).getByText("Usuario creado")).toBeTruthy();
  expect(within(success).getByText("Docente")).toBeTruthy();
  expect(calls.find((c) => c.method === "POST").body).toEqual({
    personaId: 70,
    persona: null,
    rolId: 2,
    username: "pmorales",
    correo: "pmorales@colegio.edu.pe",
    correoVerificado: true,
    password: "Temporal2026",
    codigoAlumno: null,
    codigoDocente: "DOC-2026-001",
    fechaIngreso: "2026-03-01",
    especialidad: "CIENCIA_Y_TECNOLOGIA",
    alumnoIds: [],
  });
  await user.click(screen.getByText("Crear otro usuario"));
  expect(await screen.findByText("Nuevo usuario")).toBeTruthy();
  await user.click(screen.getByText("← Volver a usuarios"));
  expect(currentUrl()).toBe("/administracion/usuarios");
});

test("Reutiliza el perfil de estudiante que la persona ya tiene", async () => {
  backend();
  const user = userEvent.setup();
  renderForm();
  await user.click(await screen.findByText("Patricia Morales"));
  await user.click(await screen.findByRole("radio", { name: /Estudiante/ }));
  expect(screen.queryByLabelText(/Código de estudiante/)).toBeNull();
  expect(screen.getByText(/Se utilizará el perfil de estudiante/)).toBeTruthy();
  expect(within(summary()).getByText("Se reutiliza el perfil existente")).toBeTruthy();
  await user.click(screen.getByText("Rosa Quispe"));
  expect(screen.getByLabelText(/Código de estudiante/)).toBeTruthy();
});

test("Registra una persona nueva como apoderado con estudiantes asociados", async () => {
  const calls = backend({
    "POST /api/usuarios/provisionar": { ...created, rol: "APODERADO" },
  });
  const user = userEvent.setup();
  renderForm();
  await user.click(screen.getByText("Persona nueva"));
  await user.type(screen.getByLabelText(/^Nombres/), "Carmen");
  await user.type(screen.getByLabelText(/^Apellido paterno/), "Flores");
  await user.type(screen.getByLabelText(/^Apellido materno/), "Vega");
  await user.type(screen.getByLabelText(/Número de documento/), "41414141");
  await user.type(screen.getByLabelText(/Fecha de nacimiento/), "1985-04-12");
  await user.selectOptions(screen.getByLabelText(/^Sexo/), "FEMENINO");
  await user.type(screen.getByLabelText(/Correo de contacto/), "carmen@correo.pe");
  await user.type(screen.getByLabelText(/^Teléfono/), "987654321");
  expect(within(summary()).getByText("Carmen Flores Vega")).toBeTruthy();
  await user.click(await screen.findByRole("radio", { name: /Apoderado/ }));
  await user.click(await screen.findByRole("checkbox", { name: /Luis García/ }));
  await user.click(screen.getByRole("checkbox", { name: /Mía García/ }));
  await user.click(screen.getByRole("checkbox", { name: /Mía García/ }));
  await user.click(screen.getByRole("checkbox", { name: /Mía García/ }));
  await user.click(screen.getByLabelText("Quitar a Mía García"));
  expect(within(summary()).getByText("Estudiantes asociados").nextSibling.textContent).toBe("1");
  await fillAccess(user);
  await user.click(screen.getByText("Crear usuario"));
  await screen.findByText("Usuario creado");
  const body = calls.find((c) => c.method === "POST").body;
  expect(body.personaId).toBeNull();
  expect(body.alumnoIds).toEqual([1]);
  expect(body.persona).toEqual({
    nombres: "Carmen",
    apellidoPaterno: "Flores",
    apellidoMaterno: "Vega",
    tipoDocumento: "DNI",
    numeroDocumento: "41414141",
    fechaNacimiento: "1985-04-12",
    sexo: "FEMENINO",
    correo: "carmen@correo.pe",
    telefono: "987654321",
    direccion: null,
    nacionalidadId: null,
  });
});

test("Rechaza una contraseña débil y acepta la generada", async () => {
  const calls = backend();
  const user = userEvent.setup();
  renderForm();
  await user.click(await screen.findByText("Rosa Quispe"));
  await user.click(await screen.findByRole("radio", { name: /Administrador/ }));
  await fillAccess(user, "pmorales");
  await user.click(screen.getByText("Crear usuario"));
  expect(screen.getByRole("alert").textContent).toMatch(/8 a 64 caracteres/);
  await user.click(screen.getByText("Generar contraseña"));
  const generated = screen.getByLabelText(/^Contraseña temporal/).value;
  expect(generated).toMatch(/^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).{16}$/);
  await user.click(screen.getByText("Crear usuario"));
  await screen.findByText("Usuario creado");
  expect(calls.find((c) => c.method === "POST").body.password).toBe(generated);
});

test("Busca personas y estudiantes con el texto escrito", async () => {
  const calls = backend({ "GET /api/usuarios/personas": page([]) });
  const user = userEvent.setup();
  renderForm();
  expect(await screen.findByText("No hay personas para esta búsqueda.")).toBeTruthy();
  await user.type(screen.getByLabelText("Buscar persona"), "quispe{Enter}");
  await waitFor(() =>
    expect(calls.some((c) => c.query.search === "quispe")).toBe(true),
  );
  await user.click(screen.getByText("Persona nueva"));
  await user.click(await screen.findByRole("radio", { name: /Apoderado/ }));
  await user.type(screen.getByLabelText("Buscar estudiante"), "garcía");
  await waitFor(() =>
    expect(calls.some((c) => c.path === "/api/alumnos" && c.query.search === "garcía")).toBe(true),
  );
});

test("Informa los errores del backend al cargar y al crear", async () => {
  backend({
    "GET /api/alumnos": reply(503, { message: "Estudiantes no disponibles" }),
    "POST /api/usuarios/provisionar": reply(409, { message: "El usuario ya existe" }),
  });
  const user = userEvent.setup();
  renderForm();
  await user.click(await screen.findByText("Rosa Quispe"));
  await user.click(await screen.findByRole("radio", { name: /Apoderado/ }));
  expect((await screen.findByRole("alert")).textContent).toBe("Estudiantes no disponibles");
  await fillAccess(user);
  await user.click(screen.getByText("Crear usuario"));
  await waitFor(() =>
    expect(screen.getAllByRole("alert").map((a) => a.textContent)).toContain("El usuario ya existe"),
  );
});

test("Si no cargan los roles se informa y Cancelar vuelve a usuarios", async () => {
  backend({ "GET /api/roles": reply(500, { message: "Sin roles" }) });
  const user = userEvent.setup();
  renderForm();
  expect((await screen.findByRole("alert")).textContent).toBe("Sin roles");
  await user.click(within(summary()).getByText("Cancelar"));
  expect(currentUrl()).toBe("/administracion/usuarios");
});
