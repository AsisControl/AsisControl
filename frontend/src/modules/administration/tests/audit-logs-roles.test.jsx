import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { currentUrl, setUrl } from "../../../../tests/support/navigation.js";
import { Administration } from "@/modules/administration";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const permission = (codigo, nombre, categoria, descripcion = null) => ({
  id: codigo.length,
  codigo,
  nombre,
  categoria,
  descripcion,
});
const readEnrollment = permission("MATRICULA_LEER", "Consultar matrícula", "Matrícula", "Ver solicitudes");
const writeUsers = permission("USUARIOS_ESCRIBIR", "Gestionar usuarios", "Usuarios");
const general = permission("PERFIL_LEER", "Ver perfil", null);
const roles = [
  { id: 1, nombre: "ADMINISTRADOR", descripcion: "", permisos: [readEnrollment, writeUsers] },
  { id: 2, nombre: "DOCENTE", descripcion: "", permisos: [general] },
  { id: 4, nombre: "APODERADO", descripcion: "", permisos: [readEnrollment] },
];

const audit = (overrides = {}) => ({
  id: 15,
  actorIdentificador: "admin",
  actorNombre: "Ana Ramos",
  rolActor: "ADMINISTRADOR",
  accion: "CAMBIAR_CONTRASENA",
  modulo: "SEGURIDAD",
  entidad: "AlumnoApoderado",
  registroId: "42",
  fechaHora: "2026-10-20T15:00:00Z",
  resultado: "EXITOSO",
  motivo: "Pedido del titular",
  correlationId: "corr-123",
  ...overrides,
});
const audits = [
  audit(),
  audit({
    id: 16,
    actorIdentificador: "SISTEMA",
    actorNombre: null,
    rolActor: null,
    accion: "CERRAR_SESION",
    modulo: "NUEVO_MODULO",
    entidad: "Sesion",
    registroId: "",
    resultado: "RECHAZADO",
    motivo: null,
  }),
];

const systemError = (overrides = {}) => ({
  id: 5,
  fechaHora: "2026-10-21T10:00:00Z",
  severidad: "CRITICA",
  componente: "MatriculaService",
  mensajeSeguro: "No se pudo generar la constancia",
  correlationId: "err-55",
  estado: "PENDIENTE",
  observacionSeguimiento: null,
  version: 3,
  ...overrides,
});

beforeEach(() => {
  signIn();
});

test("Auditoría: muestra las operaciones con etiquetas legibles y filtros", async () => {
  const calls = fakeBackend({
    "GET /api/roles": roles,
    "GET /api/auditoria/modulos": ["SEGURIDAD", "MATRICULA"],
    "GET /api/auditoria/acciones": ["CAMBIAR_CONTRASENA"],
    "GET /api/auditoria/registros": page(audits),
  });
  setUrl("/administracion/auditoria");
  const user = userEvent.setup();
  render(<Administration currentUserId="1" tab="auditoria" />);
  const first = (await screen.findByText("Ana Ramos")).closest("tr");
  expect(within(first).getByText("Cambiar contraseña")).toBeTruthy();
  expect(within(first).getByText("Estudiante apoderado #42")).toBeTruthy();
  const second = screen.getByText("Sistema").closest("tr");
  expect(within(second).getByText("Rechazado")).toBeTruthy();
  expect(within(second).getByText("Nuevo modulo")).toBeTruthy();
  expect(within(second).getByText("—")).toBeTruthy();
  await user.selectOptions(screen.getByLabelText("Filtrar por módulo"), "MATRICULA");
  await user.selectOptions(screen.getByLabelText("Filtrar por acción"), "CAMBIAR_CONTRASENA");
  await user.selectOptions(screen.getByLabelText("Filtrar por rol"), "4");
  await user.type(screen.getByLabelText("Buscar en la auditoría"), "ana");
  await waitFor(() =>
    expect(calls.at(-1).query).toMatchObject({
      modulo: "MATRICULA",
      accion: "CAMBIAR_CONTRASENA",
      rolId: "4",
      search: "ana",
    }),
  );
});

test("Auditoría: el detalle se abre con el ojo y se cierra volviendo atrás", async () => {
  fakeBackend({
    "GET /api/roles": roles,
    "GET /api/auditoria/modulos": [],
    "GET /api/auditoria/acciones": [],
    "GET /api/auditoria/registros": page(audits),
  });
  setUrl("/administracion/auditoria");
  const user = userEvent.setup();
  render(<Administration currentUserId="1" tab="auditoria" />);
  await user.click(await screen.findByLabelText("Ver detalle del registro de Sistema"));
  expect(currentUrl()).toBe("/administracion/auditoria?ver=16");
  let dialog = await screen.findByRole("dialog", { name: "Detalle del registro" });
  expect(within(dialog).getByText("Sin motivo registrado")).toBeTruthy();
  await user.click(within(dialog).getByText("Cerrar", { selector: ".primary-action" }));
  expect(currentUrl()).toBe("/administracion/auditoria");
  await user.click(screen.getByLabelText("Ver detalle del registro de Ana Ramos"));
  dialog = await screen.findByRole("dialog");
  expect(within(dialog).getByText("Pedido del titular")).toBeTruthy();
  expect(within(dialog).getByText("corr-123")).toBeTruthy();
});

test("Auditoría: un enlace compartido abre el detalle y cerrarlo limpia la URL", async () => {
  fakeBackend({
    "GET /api/roles": roles,
    "GET /api/auditoria/modulos": [],
    "GET /api/auditoria/acciones": [],
    "GET /api/auditoria/registros": page(audits),
  });
  setUrl("/administracion/auditoria?ver=15");
  const user = userEvent.setup();
  render(<Administration currentUserId="1" tab="auditoria" />);
  await screen.findByRole("dialog");
  await user.keyboard("{Escape}");
  expect(currentUrl()).toBe("/administracion/auditoria");
});

test("Auditoría: sin registros o con error del backend se informa", async () => {
  fakeBackend({
    "GET /api/roles": reply(500, { message: "Filtros no disponibles" }),
    "GET /api/auditoria/registros": page([]),
  });
  render(<Administration currentUserId="1" tab="auditoria" />);
  expect(await screen.findByText("No hay operaciones con estos filtros.")).toBeTruthy();
  expect((await screen.findByRole("alert")).textContent).toBe("Filtros no disponibles");
});

test("Logs: filtra, muestra el historial y registra un seguimiento", async () => {
  const calls = fakeBackend({
    "GET /api/auditoria/errores": page([systemError()]),
    "GET /api/auditoria/errores/:id/seguimientos": [
      {
        id: 1,
        actor: "admin",
        fecha: "2026-10-21T11:00:00Z",
        anterior: "PENDIENTE",
        nuevo: "EN_SEGUIMIENTO",
        observacion: "Revisando el servicio",
      },
    ],
    "PUT /api/auditoria/errores/:id": ({ body }) => systemError({ ...body, version: 4 }),
  });
  const user = userEvent.setup();
  render(<Administration currentUserId="1" tab="logs-del-sistema" />);
  expect(await screen.findByText("No se pudo generar la constancia")).toBeTruthy();
  expect(screen.getByText("Critica", { selector: "strong" })).toBeTruthy();
  await user.selectOptions(screen.getByLabelText("Estado"), "RESUELTO");
  await user.selectOptions(screen.getByLabelText("Severidad"), "ERROR");
  await user.type(screen.getByLabelText("Componente"), " Matricula ");
  await user.click(screen.getByText("Filtrar"));
  await waitFor(() =>
    expect(calls.at(-1).query).toMatchObject({
      estado: "RESUELTO",
      severidad: "ERROR",
      componente: "Matricula",
    }),
  );
  await user.click(await screen.findByText("Ver y dar seguimiento"));
  const dialog = screen.getByRole("dialog", { name: "Detalle y seguimiento del error" });
  expect(await within(dialog).findByText("Revisando el servicio")).toBeTruthy();
  expect(within(dialog).getByText("Pendiente → En seguimiento")).toBeTruthy();
  await user.selectOptions(within(dialog).getByLabelText(/^Estado/), "RESUELTO");
  await user.type(within(dialog).getByLabelText(/Observación/), "Se corrigió la plantilla");
  await user.click(within(dialog).getByText("Registrar seguimiento"));
  expect(await screen.findByText("Seguimiento registrado.")).toBeTruthy();
  expect(calls.find((c) => c.method === "PUT").body).toEqual({
    estado: "RESUELTO",
    observacionSeguimiento: "Se corrigió la plantilla",
    version: 3,
  });
  await user.click(within(dialog).getByLabelText("Cerrar ventana"));
  expect(screen.queryByRole("dialog")).toBeNull();
  await user.click(screen.getByText("Limpiar"));
  await user.click(screen.getByText("Actualizar"));
  await waitFor(() => expect(calls.at(-1).query.estado).toBeUndefined());
});

test("Logs: historial vacío y errores al cargar o guardar", async () => {
  fakeBackend({
    "GET /api/auditoria/errores": page([systemError({ correlationId: "" })]),
    "GET /api/auditoria/errores/:id/seguimientos": [],
    "PUT /api/auditoria/errores/:id": reply(409, { message: "Otro usuario actualizó el error" }),
  });
  const user = userEvent.setup();
  render(<Administration currentUserId="1" tab="logs-del-sistema" />);
  await user.click(await screen.findByText("Ver y dar seguimiento"));
  const dialog = screen.getByRole("dialog");
  expect(await within(dialog).findByText("Sin seguimientos registrados.")).toBeTruthy();
  await user.type(within(dialog).getByLabelText(/Observación/), "Intento");
  await user.click(within(dialog).getByText("Registrar seguimiento"));
  expect((await within(dialog).findByRole("alert")).textContent).toBe(
    "Otro usuario actualizó el error",
  );
});

test("Logs: sin errores registrados y fallo del historial", async () => {
  fakeBackend({
    "GET /api/auditoria/errores": page([systemError()]),
    "GET /api/auditoria/errores/:id/seguimientos": reply(500, { message: "Historial caído" }),
  });
  const user = userEvent.setup();
  render(<Administration currentUserId="1" tab="logs-del-sistema" />);
  await user.click(await screen.findByText("Ver y dar seguimiento"));
  expect((await screen.findByRole("alert")).textContent).toBe("Historial caído");
});

test("Logs: lista vacía", async () => {
  fakeBackend({ "GET /api/auditoria/errores": page([]) });
  render(<Administration currentUserId="1" tab="logs-del-sistema" />);
  expect(await screen.findByText("No hay errores con estos filtros.")).toBeTruthy();
});

test("Roles: muestra los permisos del rol elegido agrupados por módulo", async () => {
  fakeBackend({
    "GET /api/roles": roles,
    "GET /api/roles/permisos": [writeUsers, readEnrollment, general],
  });
  const user = userEvent.setup();
  render(<Administration currentUserId="1" tab="roles-y-permisos" />);
  expect(await screen.findByText("Permisos — Administrador")).toBeTruthy();
  expect(screen.getByRole("button", { name: /Administrador.*2 permisos/ }).getAttribute("aria-pressed")).toBe("true");
  expect(screen.getByRole("button", { name: /Estudiante.*Sin configurar/ })).toBeTruthy();
  expect(screen.getByText("2 de 3 permisos habilitados para este rol.")).toBeTruthy();
  expect(screen.getAllByRole("heading", { level: 4 }).map((h) => h.textContent)).toEqual([
    "General",
    "Matrícula",
    "Usuarios",
  ]);
  expect(screen.getByText("Ver solicitudes").closest("li").textContent).toContain("(permitido)");
  expect(screen.getByText("Ver perfil").closest("li").textContent).toContain("(sin permiso)");
  expect(screen.queryByText(/Guardar/)).toBeNull();
  await user.click(screen.getByRole("button", { name: /Docente/ }));
  expect(screen.getByText("Permisos — Docente")).toBeTruthy();
  expect(screen.getByText("Ver perfil").closest("li").textContent).toContain("(permitido)");
  expect(screen.getByText("1 de 3 permisos habilitados para este rol.")).toBeTruthy();
  await user.type(screen.getByLabelText("Buscar permiso o módulo"), "matrícula");
  expect(screen.getAllByRole("heading", { level: 4 }).map((h) => h.textContent)).toEqual(["Matrícula"]);
  await user.clear(screen.getByLabelText("Buscar permiso o módulo"));
  await user.type(screen.getByLabelText("Buscar permiso o módulo"), "inexistente");
  expect(screen.getByText("No hay permisos que coincidan con la búsqueda.")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: /Estudiante/ }));
  expect(screen.getByText("Este rol todavía no está configurado en el servidor.")).toBeTruthy();
});

test("Roles: informa el error y permite volver a cargar", async () => {
  let attempts = 0;
  fakeBackend({
    "GET /api/roles": () => (++attempts === 1 ? reply(500, { message: "Sin conexión" }) : roles),
    "GET /api/roles/permisos": [],
  });
  const user = userEvent.setup();
  render(<Administration currentUserId="1" tab="roles-y-permisos" />);
  expect((await screen.findByRole("alert")).textContent).toBe("Sin conexión");
  await user.click(screen.getByText("Reintentar"));
  expect(await screen.findByText("0 de 0 permisos habilitados para este rol.")).toBeTruthy();
  expect(screen.queryByRole("alert")).toBeNull();
  await user.click(screen.getByRole("button", { name: "Actualizar" }));
  expect(attempts).toBe(3);
});
