import { beforeEach, expect, test, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { currentUrl, setUrl } from "../../../../tests/support/navigation.js";
import { Administration } from "@/modules/administration";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const roles = [
  { id: 1, nombre: "ADMINISTRADOR", descripcion: "", permisos: [] },
  { id: 2, nombre: "DOCENTE", descripcion: "", permisos: [] },
  { id: 9, nombre: "TESORERIA", descripcion: "", permisos: [] },
];

const account = (overrides = {}) => ({
  id: 7,
  personaId: 70,
  username: "pmorales",
  correo: "pmorales@colegio.edu.pe",
  nombreCompleto: "Patricia Morales",
  rolId: 2,
  rol: "DOCENTE",
  roles: [
    { id: 2, nombre: "DOCENTE", activo: true, codigoPerfil: "DOC20260001" },
    { id: 4, nombre: "APODERADO", activo: true, codigoPerfil: "APO20260001" },
  ],
  estado: "ACTIVO",
  requiereCambioContrasena: true,
  intentosFallidos: 2,
  bloqueadoHasta: "2026-10-01T15:00:00Z",
  ultimoAcceso: "2026-10-20T13:30:00Z",
  fechaCreacion: "2026-03-01T13:00:00Z",
  correoVerificado: true,
  contrasenaTemporalExpira: "2026-10-24T13:00:00Z",
  ...overrides,
});

const admin = account({
  id: 1,
  username: "admin",
  nombreCompleto: "Ana Ramos",
  rol: "ADMINISTRADOR",
  rolId: 1,
  roles: [{ id: 1, nombre: "ADMINISTRADOR", activo: true, codigoPerfil: "ADM20260001" }],
  ultimoAcceso: null,
});
const inactive = account({
  id: 8,
  username: "lgarcia",
  nombreCompleto: "Luis García",
  rol: "ALUMNO",
  roles: [{ id: 3, nombre: "ALUMNO", activo: false, codigoPerfil: "EST20260001" }],
  estado: "DESACTIVADO",
  requiereCambioContrasena: false,
  correoVerificado: false,
  bloqueadoHasta: null,
});
const accounts = { 1: admin, 7: account(), 8: inactive };

function backend(overrides = {}) {
  return fakeBackend({
    "GET /api/roles": roles,
    "GET /api/usuarios": page([admin, account(), inactive]),
    "PUT /api/usuarios/:id/contrasena-temporal": reply(204),
    "GET /api/usuarios/:id": ({ path }) => accounts[path.split("/").at(-1)],
    "PUT /api/usuarios/:id": ({ body }) => ({ ...account(), ...body }),
    ...overrides,
  });
}

const renderUsers = () =>
  render(<Administration currentUserId="1" tab="usuarios" />);

async function openMenu(user, name, option) {
  await user.click(await screen.findByLabelText(`Más acciones para ${name}`));
  await user.click(screen.getByRole("menuitem", { name: option }));
}

beforeEach(() => {
  signIn();
  setUrl("/administracion/usuarios");
});

test("Lista los accesos con su rol, estado y último acceso", async () => {
  const calls = backend();
  renderUsers();
  expect(await screen.findByText("3 de 3 usuarios")).toBeTruthy();
  const row = screen.getByText("Patricia Morales").closest("tr");
  expect(within(row).getByText("Docente")).toBeTruthy();
  expect(within(row).getByText("Apoderado")).toBeTruthy();
  expect(within(row).getByText("Activo")).toBeTruthy();
  expect(within(screen.getByText("Ana Ramos").closest("tr")).getByText("Sin registro")).toBeTruthy();
  expect(screen.getByText("Inactivo", { selector: "span" })).toBeTruthy();
  expect(within(screen.getByText("Luis García").closest("tr")).getByText("Estudiante (inactivo)")).toBeTruthy();
  const roleFilter = screen.getByLabelText("Filtrar por rol");
  expect(within(roleFilter).queryByText("TESORERIA")).toBeNull();
  expect(calls.find((c) => c.path === "/api/usuarios").query).toMatchObject({
    page: "0",
    size: "20",
  });
});

test("Muestra una cuenta sin roles y reconoce la representación anterior de un rol activo", async () => {
  backend({
    "GET /api/usuarios": page([
      account({ roles: undefined }),
      account({
        id: 10, username: "20260010", nombreCompleto: "Elena Prado", roles: undefined,
        rol: null, rolId: null,
      }),
    ]),
  });
  renderUsers();
  const legacy = (await screen.findByText("Patricia Morales")).closest("tr");
  expect(within(legacy).getByText("Docente")).toBeTruthy();
  const noRoles = screen.getByText("Elena Prado").closest("tr");
  expect(within(noRoles).getByText("Sin roles")).toBeTruthy();
  expect(within(noRoles).getByText("Activo")).toBeTruthy();
});

test("Retira un rol sin mandar cambios de cuenta ni de credencial", async () => {
  const { promise: response, resolve } = Promise.withResolvers();
  const calls = backend({ "PUT /api/usuarios/7/roles/2": () => response });
  const user = userEvent.setup();
  renderUsers();
  await openMenu(user, "Patricia Morales", "Administrar roles");
  const dialog = await screen.findByRole("dialog", { name: "Administrar roles" });
  await user.selectOptions(within(dialog).getByLabelText(/^Rol/), "2");
  await user.type(within(dialog).getByLabelText(/^Motivo/), "Cese del perfil docente");
  await user.click(within(dialog).getByRole("button", { name: "Retirar rol" }));
  const pending = within(dialog).getByRole("button", { name: "Guardando…" });
  expect(pending.disabled).toBe(true);
  fireEvent.submit(pending.closest("form"));
  expect(calls.filter((call) => call.method === "PUT")).toHaveLength(1);
  resolve(account());
  expect(await screen.findByText("Roles actualizados.")).toBeTruthy();
  expect(calls.find((c) => c.method === "PUT").body).toEqual({
    activo: false,
    motivo: "Cese del perfil docente",
  });
});

test("Exige un motivo con texto al administrar roles y Cancela sin guardar", async () => {
  const calls = backend();
  const user = userEvent.setup();
  renderUsers();
  await openMenu(user, "Patricia Morales", "Administrar roles");
  const dialog = within(await screen.findByRole("dialog", { name: "Administrar roles" }));
  expect(dialog.getByRole("button", { name: "Habilitar rol" }).disabled).toBe(true);
  await user.selectOptions(dialog.getByLabelText(/^Rol/), "2");
  await user.type(dialog.getByLabelText(/^Motivo/), "   ");
  await user.click(dialog.getByRole("button", { name: "Retirar rol" }));
  expect(dialog.getByRole("alert").textContent).toBe("Ingresa el motivo del cambio.");
  await user.selectOptions(dialog.getByLabelText(/^Rol/), "1");
  expect(dialog.queryByRole("alert")).toBeNull();
  await user.click(dialog.getByRole("button", { name: "Cancelar" }));
  expect(screen.queryByRole("dialog", { name: "Administrar roles" })).toBeNull();
  expect(calls.some((call) => call.method === "PUT")).toBe(false);
});

test("Al asignar un rol sin perfil muestra el requisito que devuelve el servidor", async () => {
  const calls = backend({
    "PUT /api/usuarios/7/roles/1": reply(409, { message: "La persona necesita un perfil habilitado." }),
  });
  const user = userEvent.setup();
  renderUsers();
  await openMenu(user, "Patricia Morales", "Administrar roles");
  const dialog = await screen.findByRole("dialog", { name: "Administrar roles" });
  await user.selectOptions(within(dialog).getByLabelText(/^Rol/), "1");
  await user.type(within(dialog).getByLabelText(/^Motivo/), "Alta de un perfil de gestión");
  await user.click(within(dialog).getByRole("button", { name: "Habilitar rol" }));
  expect((await within(dialog).findByRole("alert")).textContent)
    .toBe("La persona necesita un perfil habilitado.");
  expect(calls.find((c) => c.method === "PUT").body).toMatchObject({ activo: true });
});

test("Filtra por rol, estado y texto de búsqueda", async () => {
  const calls = backend();
  const user = userEvent.setup();
  renderUsers();
  await screen.findByText("3 de 3 usuarios");
  await user.selectOptions(screen.getByLabelText("Filtrar por rol"), "2");
  await user.selectOptions(screen.getByLabelText("Filtrar por estado"), "PENDIENTE");
  await user.type(screen.getByLabelText("Buscar usuarios"), " morales ");
  await waitFor(() =>
    expect(calls.at(-1).query).toMatchObject({
      rolId: "2",
      estado: "PENDIENTE",
      search: "morales",
    }),
  );
});

test("Sin resultados muestra el mensaje vacío y un error del backend se informa", async () => {
  backend({
    "GET /api/usuarios": page([]),
    "GET /api/roles": reply(500, { message: "Roles no disponibles" }),
  });
  renderUsers();
  expect(await screen.findByText("No se encontraron usuarios.")).toBeTruthy();
  expect((await screen.findByRole("alert")).textContent).toBe("Roles no disponibles");
});

test("El detalle se abre desde la URL y pasa a la edición", async () => {
  const calls = backend();
  const user = userEvent.setup();
  renderUsers();
  await user.click(await screen.findByLabelText("Ver detalle de Patricia Morales"));
  expect(currentUrl()).toBe("/administracion/usuarios?ver=7");
  const dialog = await screen.findByRole("dialog", { name: "Detalle del usuario" });
  expect(within(dialog).getByText("pmorales@colegio.edu.pe")).toBeTruthy();
  expect(within(dialog).getByText("Confirmado")).toBeTruthy();
  expect(within(dialog).getByText("Bloqueado hasta")).toBeTruthy();
  expect(within(dialog).getByText(/Pendiente · temporal vence/)).toBeTruthy();
  await user.click(within(dialog).getByText("Editar acceso"));
  expect(currentUrl()).toBe("/administracion/usuarios?editar=7");
  expect(await screen.findByRole("dialog", { name: "Editar acceso" })).toBeTruthy();
  expect(calls.filter((c) => c.path === "/api/usuarios/7")).toHaveLength(1);
});

test("Cerrar una ventana abierta desde la lista vuelve atrás en el historial", async () => {
  backend();
  const user = userEvent.setup();
  renderUsers();
  await user.click(await screen.findByLabelText("Ver detalle de Patricia Morales"));
  await screen.findByRole("dialog");
  await user.click(screen.getByText("Cerrar", { selector: ".form-actions button" }));
  expect(currentUrl()).toBe("/administracion/usuarios");
  await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
});

test("Un enlace compartido abre la ventana y la cierra con Escape", async () => {
  backend();
  setUrl("/administracion/usuarios?ver=8");
  const user = userEvent.setup();
  renderUsers();
  const dialog = await screen.findByRole("dialog", { name: "Detalle del usuario" });
  expect(within(dialog).getByText("Al día")).toBeTruthy();
  expect(within(dialog).getByText("Pendiente de confirmar")).toBeTruthy();
  await user.keyboard("{Escape}");
  expect(currentUrl()).toBe("/administracion/usuarios");
});

test("Si el usuario del enlace no existe, se informa y se limpia la URL", async () => {
  backend({ "GET /api/usuarios/:id": reply(404, { message: "Usuario no encontrado" }) });
  setUrl("/administracion/usuarios?editar=99");
  renderUsers();
  expect((await screen.findByRole("alert")).textContent).toBe("Usuario no encontrado");
  expect(currentUrl()).toBe("/administracion/usuarios");
});

test("Editar envía correo, estado, verificación y motivo sin cambiar el rol", async () => {
  const calls = backend();
  const user = userEvent.setup();
  renderUsers();
  await openMenu(user, "Patricia Morales", "Editar");
  const dialog = await screen.findByRole("dialog", { name: "Editar acceso" });
  const email = within(dialog).getByLabelText(/Correo del acceso/);
  await user.clear(email);
  await user.type(email, "patricia@colegio.edu.pe");
  expect(within(dialog).getByRole("checkbox").checked).toBe(false);
  await user.type(within(dialog).getByLabelText(/Motivo del cambio/), "Correo nuevo");
  await user.click(within(dialog).getByText("Guardar cambios"));
  expect(await screen.findByText("Acceso actualizado.")).toBeTruthy();
  expect(calls.find((c) => c.method === "PUT").body).toEqual({
    correo: "patricia@colegio.edu.pe",
    estado: "ACTIVO",
    correoVerificado: false,
    temporaryPassword: null,
    motivo: "Correo nuevo",
  });
});

test("Reactivar exige una contraseña temporal válida", async () => {
  const calls = backend();
  const user = userEvent.setup();
  renderUsers();
  await openMenu(user, "Luis García", "Editar");
  const dialog = await screen.findByRole("dialog", { name: "Editar acceso" });
  await user.selectOptions(within(dialog).getByLabelText("Estado"), "ACTIVO");
  await user.type(within(dialog).getByLabelText(/Nueva contraseña temporal/), "corta");
  await user.type(within(dialog).getByLabelText(/Motivo del cambio/), "Regresa");
  await user.click(within(dialog).getByText("Guardar cambios"));
  expect(within(dialog).getByRole("alert").textContent).toMatch(/8 a 64 caracteres/);
  const password = within(dialog).getByLabelText(/Nueva contraseña temporal/);
  await user.clear(password);
  await user.type(password, "Temporal2026");
  await user.click(within(dialog).getByText("Guardar cambios"));
  await screen.findByText("Acceso actualizado.");
  expect(calls.find((c) => c.method === "PUT").body.temporaryPassword).toBe("Temporal2026");
});

test("El administrador puede gestionar roles, pero su cuenta no ofrece desactivarse", async () => {
  backend();
  const user = userEvent.setup();
  renderUsers();
  await user.click(await screen.findByLabelText("Más acciones para Ana Ramos"));
  expect(screen.getAllByRole("menuitem").map((item) => item.textContent)).toEqual([
    "Editar correo",
    "Administrar roles",
  ]);
  await user.click(screen.getByRole("menuitem", { name: "Editar correo" }));
  const dialog = await screen.findByRole("dialog", { name: "Editar acceso" });
  expect(within(dialog).queryByText("Inactivo")).toBeNull();
});

test("Un error al guardar se muestra dentro de la ventana", async () => {
  backend({ "PUT /api/usuarios/:id": reply(409, { message: "El correo ya está en uso" }) });
  const user = userEvent.setup();
  renderUsers();
  await openMenu(user, "Patricia Morales", "Editar");
  const dialog = await screen.findByRole("dialog", { name: "Editar acceso" });
  await user.type(within(dialog).getByLabelText(/Motivo del cambio/), "Cambio");
  await user.click(within(dialog).getByText("Guardar cambios"));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("El correo ya está en uso");
});

test("Restablecer valida la contraseña y revoca las sesiones", async () => {
  const calls = backend();
  const user = userEvent.setup();
  renderUsers();
  await openMenu(user, "Patricia Morales", "Restablecer contraseña");
  const dialog = await screen.findByRole("dialog", { name: "Restablecer contraseña" });
  await user.click(within(dialog).getByRole("checkbox"));
  await user.type(within(dialog).getByLabelText(/^Motivo/), "Olvidó su clave");
  await user.type(within(dialog).getByLabelText(/Contraseña temporal/), "pmorales");
  await user.click(within(dialog).getByText("Restablecer"));
  expect(within(dialog).getByRole("alert")).toBeTruthy();
  await user.click(within(dialog).getByLabelText("Mostrar contraseña"));
  const password = within(dialog).getByLabelText(/Contraseña temporal/);
  expect(password.type).toBe("text");
  await user.clear(password);
  await user.type(password, "Nueva2026x");
  await user.click(within(dialog).getByText("Restablecer"));
  expect(await screen.findByText(/sesiones anteriores fueron revocadas/)).toBeTruthy();
  expect(calls.find((c) => c.method === "PUT").body).toEqual({
    temporaryPassword: "Nueva2026x",
    identidadVerificada: true,
    motivo: "Olvidó su clave",
  });
});

test("Restablecer informa el rechazo del backend", async () => {
  backend({
    "PUT /api/usuarios/:id/contrasena-temporal": reply(400, { message: "Contraseña usada antes" }),
  });
  const user = userEvent.setup();
  renderUsers();
  await openMenu(user, "Patricia Morales", "Restablecer contraseña");
  const dialog = await screen.findByRole("dialog");
  await user.click(within(dialog).getByRole("checkbox"));
  await user.type(within(dialog).getByLabelText(/^Motivo/), "Pedido");
  await user.type(within(dialog).getByLabelText(/Contraseña temporal/), "Nueva2026x");
  await user.click(within(dialog).getByText("Restablecer"));
  expect((await within(dialog).findByRole("alert")).textContent).toBe("Contraseña usada antes");
});

test("Desactivar conserva el correo y registra el motivo", async () => {
  const calls = backend();
  const user = userEvent.setup();
  renderUsers();
  await openMenu(user, "Patricia Morales", "Desactivar");
  const dialog = await screen.findByRole("dialog", { name: "Desactivar usuario" });
  await user.type(within(dialog).getByLabelText(/Motivo de la desactivación/), "Dejó la institución");
  await user.click(within(dialog).getByText("Desactivar", { selector: ".primary-action" }));
  expect(await screen.findByText("Usuario desactivado.")).toBeTruthy();
  expect(calls.find((c) => c.method === "PUT").body).toMatchObject({
    correo: "pmorales@colegio.edu.pe",
    estado: "DESACTIVADO",
    motivo: "Dejó la institución",
  });
});

test("Desactivar informa el error y un acceso inactivo no ofrece desactivarse", async () => {
  backend({ "PUT /api/usuarios/:id": reply(500, null) });
  const user = userEvent.setup();
  renderUsers();
  await user.click(await screen.findByLabelText("Más acciones para Luis García"));
  expect(screen.queryByRole("menuitem", { name: "Desactivar" })).toBeNull();
  await user.keyboard("{Escape}");
  expect(screen.queryByRole("menu")).toBeNull();
  await openMenu(user, "Patricia Morales", "Desactivar");
  const dialog = await screen.findByRole("dialog");
  await user.type(within(dialog).getByLabelText(/Motivo/), "Baja");
  await user.click(within(dialog).getByText("Desactivar", { selector: ".primary-action" }));
  expect((await within(dialog).findByRole("alert")).textContent).toBe(
    "No se pudo completar la operación.",
  );
  await user.click(within(dialog).getByText("Cancelar"));
  expect(currentUrl()).toBe("/administracion/usuarios");
});

test("El menú de acciones se cierra al hacer clic fuera o al volver a pulsarlo", async () => {
  backend();
  const user = userEvent.setup();
  renderUsers();
  const toggle = await screen.findByLabelText("Más acciones para Patricia Morales");
  await user.click(toggle);
  expect(toggle.getAttribute("aria-expanded")).toBe("true");
  await user.click(toggle);
  expect(screen.queryByRole("menu")).toBeNull();
  await user.click(toggle);
  await user.click(document.body);
  expect(screen.queryByRole("menu")).toBeNull();
});

test("Las pestañas navegan por URL y una ruta inválida vuelve a usuarios", async () => {
  backend();
  const user = userEvent.setup();
  const { rerender } = renderUsers();
  await user.click(screen.getByText("Roles y permisos"));
  expect(currentUrl()).toBe("/administracion/roles-y-permisos");
  await user.click(screen.getByText("+ Registrar administrador"));
  expect(currentUrl()).toBe("/administracion/usuarios/nuevo");
  rerender(<Administration currentUserId="1" tab="desconocida" />);
  expect(currentUrl()).toBe("/administracion/usuarios");
  rerender(<Administration currentUserId="1" tab="auditoria" view="nuevo" />);
  expect(currentUrl()).toBe("/administracion/usuarios");
});
