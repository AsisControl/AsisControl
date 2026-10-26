import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { currentUrl, setUrl } from "../../../../tests/support/navigation.js";
import { Schedules } from "@/modules/schedules";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));

const classroom = (overrides = {}) => ({
  id: 1,
  codigo: "AULA-201",
  nombre: "Aula 201",
  tipoAula: "AULA_REGULAR",
  capacidad: 35,
  ubicacion: "Pabellón A — 2° piso",
  activo: true,
  version: 4,
  ...overrides,
});
const lab = classroom({
  id: 2,
  codigo: "LAB-01",
  nombre: "Lab. 01",
  tipoAula: "LABORATORIO_COMPUTO",
  capacidad: 1,
  ubicacion: "",
});
const closed = classroom({ id: 3, codigo: "AULA-302", nombre: "Aula 302", activo: false, version: 7 });

const schedule = (id, diaSemana, horaInicio, activo = true) => ({
  id,
  asignacionCursoId: id * 10,
  cursoId: 5,
  curso: "Matemática",
  seccionId: 8,
  seccion: "A",
  docenteId: 9,
  docente: "Patricia Morales",
  aulaId: 1,
  aula: "Aula 201",
  diaSemana,
  horaInicio,
  horaFin: "09:30:00",
  activo,
  version: 0,
});

function backend(routes = {}) {
  return fakeBackend({
    "GET /api/aulas": page([classroom(), lab, closed]),
    "GET /api/aulas/resumen": { habilitadas: 2 },
    ...routes,
  });
}

const renderSchedules = (props = {}) => render(<Schedules canWrite {...props} />);
const rowOf = async (name) => (await screen.findByText(name, { selector: "strong" })).closest("tr");
async function chooseAction(user, name, action) {
  await user.click(within(await rowOf(name)).getByLabelText(`Más acciones para ${name}`));
  await user.click(screen.getByRole("menuitem", { name: action }));
}

beforeEach(() => {
  signIn({ permissions: ["ACADEMICO_LEER", "ACADEMICO_ESCRIBIR"] });
  setUrl("/horarios/aulas");
});

test("Lista todas las aulas con ubicación, capacidad y estado", async () => {
  const calls = backend();
  renderSchedules();
  const first = await rowOf("Aula 201");
  expect(within(first).getByText("AULA-201")).toBeTruthy();
  expect(within(first).getByText("Pabellón A — 2° piso")).toBeTruthy();
  expect(within(first).getByText("35 estudiantes")).toBeTruthy();
  expect(within(first).getByText("Disponible").className).toContain("status-success");
  const second = await rowOf("Lab. 01");
  expect(within(second).getByText("Laboratorio de cómputo")).toBeTruthy();
  expect(within(second).getByText("1 estudiante")).toBeTruthy();
  expect(within(await rowOf("Aula 302")).getByText("Inhabilitada").className).toContain("status-warning");
  expect(await screen.findByText(/3 aulas registradas · 2 disponibles · Última actualización: \d{2}:\d{2}/)).toBeTruthy();
  expect(screen.getByText("Las aulas inhabilitadas no pueden asignarse a nuevos bloques de horario.")).toBeTruthy();
  expect(screen.getByRole("columnheader", { name: /Equipamiento/ }).textContent).toContain("Próximamente");
  const list = calls.find((c) => c.path === "/api/aulas");
  expect(list.query).toMatchObject({ estado: "TODAS", sort: "codigo", page: "0", size: "20" });
});

test("Sin aulas lo indica y, si el resumen falla, omite las disponibles", async () => {
  backend({
    "GET /api/aulas": page([]),
    "GET /api/aulas/resumen": reply(500, { message: "Error" }),
  });
  renderSchedules();
  expect(await screen.findByText("Aún no hay aulas registradas. Usa «Nueva aula» para registrar la primera.")).toBeTruthy();
  expect(screen.getByText(/^0 aulas registradas · Última/)).toBeTruthy();
});

test("Muestra el error del listado y permite reintentar", async () => {
  let fail = true;
  const calls = backend({
    "GET /api/aulas": () => (fail ? reply(403, null) : page([classroom()])),
  });
  const user = userEvent.setup();
  renderSchedules();
  expect((await screen.findByRole("alert")).textContent).toBe("No tienes permiso para realizar esta operación.");
  expect(screen.queryByText(/Aún no hay aulas/)).toBeNull();
  fail = false;
  await user.click(screen.getByText("Actualizar"));
  expect(await rowOf("Aula 201")).toBeTruthy();
  expect(calls.filter((c) => c.path === "/api/aulas/resumen")).toHaveLength(2);
});

test("Sin permiso de escritura solo se consulta", async () => {
  backend({ "GET /api/aulas/:id/horarios": [] });
  const user = userEvent.setup();
  renderSchedules({ canWrite: false });
  const row = await rowOf("Aula 201");
  expect(screen.queryByText("+ Nueva aula")).toBeNull();
  expect(within(row).queryByLabelText(/Más acciones/)).toBeNull();
  await user.click(within(row).getByText("Ver detalle"));
  const dialog = screen.getByRole("dialog", { name: "Aula 201" });
  expect(await within(dialog).findByText("Esta aula aún no se usa en ningún horario.")).toBeTruthy();
  expect(within(dialog).queryByText("Editar aula")).toBeNull();
});

test("El detalle muestra los datos y los horarios ordenados, y lleva a la edición", async () => {
  const calls = backend({
    "GET /api/aulas/:id/horarios": [
      schedule(1, "MIERCOLES", "10:00:00"),
      schedule(2, "LUNES", "08:00:00", false),
      schedule(3, "LUNES", "11:00:00"),
    ],
  });
  const user = userEvent.setup();
  renderSchedules();
  await user.click(within(await rowOf("Aula 201")).getByText("Ver detalle"));
  const dialog = screen.getByRole("dialog", { name: "Aula 201" });
  expect(within(dialog).getByText("Aula · AULA-201")).toBeTruthy();
  expect(within(dialog).getByText("Aula regular")).toBeTruthy();
  expect(within(dialog).getByText("Pabellón A — 2° piso")).toBeTruthy();
  expect(await within(dialog).findByText("2 bloques activos")).toBeTruthy();
  expect(within(dialog).getAllByRole("listitem").map((item) => item.querySelector("strong").textContent)).toEqual([
    "Lunes 11:00 – 09:30",
    "Miércoles 10:00 – 09:30",
    "Lunes 08:00 – 09:30",
  ]);
  expect(within(dialog).getAllByText("Matemática · Sección A · Patricia Morales")).toHaveLength(3);
  expect(within(dialog).getByText("Inactivo")).toBeTruthy();
  expect(calls.some((c) => c.path === "/api/aulas/1/horarios")).toBe(true);
  await user.click(within(dialog).getByText("Editar aula"));
  expect(screen.getByRole("dialog", { name: "Editar aula" })).toBeTruthy();
});

test("El detalle avisa si no se pudieron cargar los horarios", async () => {
  backend({ "GET /api/aulas/:id/horarios": reply(404, { message: "No existe un aula con id 2" }) });
  const user = userEvent.setup();
  renderSchedules();
  await user.click(within(await rowOf("Lab. 01")).getByText("Ver detalle"));
  const dialog = screen.getByRole("dialog", { name: "Lab. 01" });
  expect((await within(dialog).findByRole("alert")).textContent).toBe("No existe un aula con id 2");
  expect(within(dialog).getByText("Sin ubicación")).toBeTruthy();
  await user.click(within(dialog).getByText("Cerrar", { selector: ".form-actions button" }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("El formulario valida los campos antes de enviar", async () => {
  const calls = backend();
  const user = userEvent.setup();
  renderSchedules();
  await rowOf("Aula 201");
  await user.click(screen.getByText("+ Nueva aula"));
  const dialog = screen.getByRole("dialog", { name: "Nueva aula" });
  expect(within(dialog).getByLabelText(/^Estado/).disabled).toBe(true);
  expect(within(dialog).getByLabelText(/^Equipamiento/).disabled).toBe(true);
  await user.selectOptions(within(dialog).getByLabelText(/^Tipo de aula/), "");
  await user.click(within(dialog).getByText("Guardar aula"));
  expect(within(dialog).getByText("El código del aula es obligatorio.")).toBeTruthy();
  expect(within(dialog).getByText("El nombre del aula es obligatorio.")).toBeTruthy();
  expect(within(dialog).getByText("La capacidad es obligatoria.")).toBeTruthy();
  expect(within(dialog).getByText("Seleccione el tipo de aula.")).toBeTruthy();
  expect(within(dialog).getByLabelText(/^Código/).getAttribute("aria-invalid")).toBe("true");
  await user.type(within(dialog).getByLabelText(/^Código/), " aula-201 ");
  expect(within(dialog).queryByText("El código del aula es obligatorio.")).toBeNull();
  await user.type(within(dialog).getByLabelText(/^Capacidad/), "301");
  await user.click(within(dialog).getByText("Guardar aula"));
  expect(within(dialog).getByText("Ya existe un aula con ese código.")).toBeTruthy();
  expect(within(dialog).getByText("La capacidad debe estar entre 1 y 300 estudiantes.")).toBeTruthy();
  expect(calls.some((c) => c.method === "POST")).toBe(false);
});

test("Registra un aula con los datos del formulario", async () => {
  const calls = backend({
    "POST /api/aulas": ({ body }) => reply(201, classroom({ id: 9, ...body, codigo: body.codigo.toUpperCase() })),
  });
  const user = userEvent.setup();
  renderSchedules();
  await rowOf("Aula 201");
  await user.click(screen.getByText("+ Nueva aula"));
  const dialog = screen.getByRole("dialog", { name: "Nueva aula" });
  await user.type(within(dialog).getByLabelText(/^Código/), " aula-305 ");
  await user.type(within(dialog).getByLabelText(/^Nombre del aula/), " Aula 305 ");
  await user.type(within(dialog).getByLabelText(/^Capacidad/), "30");
  await user.selectOptions(within(dialog).getByLabelText(/^Tipo de aula/), "TALLER");
  await user.click(within(dialog).getByText("Guardar aula"));
  expect(await screen.findByText("Aula Aula 305 registrada.")).toBeTruthy();
  expect(calls.find((c) => c.method === "POST").body).toEqual({
    codigo: "aula-305",
    nombre: "Aula 305",
    tipoAula: "TALLER",
    capacidad: 30,
    ubicacion: null,
  });
  await waitFor(() => expect(calls.filter((c) => c.method === "GET" && c.path === "/api/aulas")).toHaveLength(2));
});

test("Muestra en cada campo los errores que devuelve el servidor", async () => {
  backend({
    "POST /api/aulas": reply(400, {
      message: "Hay campos con errores",
      fieldErrors: [{ field: "nombre", message: "no debe estar vacío" }],
    }),
  });
  const user = userEvent.setup();
  renderSchedules();
  await rowOf("Aula 201");
  await user.click(screen.getByText("+ Nueva aula"));
  const dialog = screen.getByRole("dialog", { name: "Nueva aula" });
  await user.type(within(dialog).getByLabelText(/^Código/), "AULA-400");
  await user.type(within(dialog).getByLabelText(/^Nombre del aula/), "Aula 400");
  await user.type(within(dialog).getByLabelText(/^Capacidad/), "20");
  await user.click(within(dialog).getByText("Guardar aula"));
  expect(await within(dialog).findByText("Hay campos con errores")).toBeTruthy();
  expect(within(dialog).getByText("no debe estar vacío")).toBeTruthy();
  expect(within(dialog).getByText("Guardar aula").disabled).toBe(false);
});

test("Edita un aula enviando su versión y muestra los conflictos del servidor", async () => {
  const calls = backend({
    "PUT /api/aulas/:id": ({ body }) =>
      body.capacidad < 30
        ? reply(409, { message: "La nueva capacidad es menor que la capacidad maxima de secciones asignadas al aula" })
        : classroom({ ...body, version: 5 }),
  });
  const user = userEvent.setup();
  renderSchedules();
  await chooseAction(user, "Aula 201", "Editar");
  const dialog = screen.getByRole("dialog", { name: "Editar aula" });
  expect(within(dialog).getByText("Aula 201 · AULA-201")).toBeTruthy();
  const capacity = within(dialog).getByLabelText(/^Capacidad/);
  expect(capacity.value).toBe("35");
  expect(within(dialog).getByLabelText(/^Ubicación/).value).toBe("Pabellón A — 2° piso");
  await user.clear(capacity);
  await user.type(capacity, "20");
  await user.click(within(dialog).getByText("Guardar cambios"));
  expect(await within(dialog).findByText(/La nueva capacidad es menor/)).toBeTruthy();
  await user.clear(capacity);
  await user.type(capacity, "40");
  await user.click(within(dialog).getByText("Guardar cambios"));
  expect(await screen.findByText("Aula Aula 201 actualizada.")).toBeTruthy();
  expect(calls.filter((c) => c.method === "PUT").at(-1)).toMatchObject({
    path: "/api/aulas/1",
    body: {
      codigo: "AULA-201",
      nombre: "Aula 201",
      tipoAula: "AULA_REGULAR",
      capacidad: 40,
      ubicacion: "Pabellón A — 2° piso",
      version: 4,
    },
  });
});

test("Inhabilitar exige motivo y muestra el rechazo por horarios activos", async () => {
  let blocked = true;
  const calls = backend({
    "PATCH /api/aulas/:id/inhabilitar": ({ body }) =>
      blocked
        ? reply(409, { message: "No se puede inhabilitar un aula con horarios activos" })
        : classroom({ activo: false, version: body.version + 1 }),
  });
  const user = userEvent.setup();
  renderSchedules();
  await chooseAction(user, "Aula 201", "Inhabilitar");
  const dialog = screen.getByRole("dialog", { name: "Inhabilitar aula" });
  await user.click(within(dialog).getByText("Inhabilitar aula", { selector: "button" }));
  expect(within(dialog).getByText("Indique el motivo del cambio de estado.")).toBeTruthy();
  expect(calls.some((c) => c.method === "PATCH")).toBe(false);
  await user.type(within(dialog).getByLabelText(/^Motivo/), " Mantenimiento ");
  await user.click(within(dialog).getByText("Inhabilitar aula", { selector: "button" }));
  expect(await within(dialog).findByText("No se puede inhabilitar un aula con horarios activos")).toBeTruthy();
  blocked = false;
  await user.click(within(dialog).getByText("Inhabilitar aula", { selector: "button" }));
  expect(await screen.findByText("Aula Aula 201 inhabilitada.")).toBeTruthy();
  expect(calls.filter((c) => c.method === "PATCH").at(-1)).toMatchObject({
    path: "/api/aulas/1/inhabilitar",
    body: { motivo: "Mantenimiento", version: 4 },
  });
});

test("Habilitar deja el motivo opcional", async () => {
  const calls = backend({
    "PATCH /api/aulas/:id/habilitar": { ...closed, activo: true, version: 8 },
  });
  const user = userEvent.setup();
  renderSchedules();
  await chooseAction(user, "Aula 302", "Habilitar");
  const dialog = screen.getByRole("dialog", { name: "Habilitar aula" });
  await user.click(within(dialog).getByText("Habilitar aula", { selector: "button" }));
  expect(await screen.findByText("Aula Aula 302 habilitada.")).toBeTruthy();
  expect(calls.find((c) => c.method === "PATCH")).toMatchObject({
    path: "/api/aulas/3/habilitar",
    body: { motivo: null, version: 7 },
  });
});

test("Eliminar muestra el rechazo de un aula usada y elimina una sin horarios", async () => {
  const calls = backend({
    "DELETE /api/aulas/:id": ({ path }) =>
      path.endsWith("/1")
        ? reply(409, { message: "No se puede eliminar un aula utilizada en horarios; inhabilitela en su lugar" })
        : reply(204),
  });
  const user = userEvent.setup();
  renderSchedules();
  await chooseAction(user, "Aula 201", "Eliminar");
  let dialog = screen.getByRole("dialog", { name: "Eliminar aula" });
  await user.click(within(dialog).getByText("Eliminar aula", { selector: "button" }));
  expect(await within(dialog).findByText(/No se puede eliminar un aula utilizada/)).toBeTruthy();
  await user.click(within(dialog).getByText("Cancelar"));
  await chooseAction(user, "Lab. 01", "Eliminar");
  dialog = screen.getByRole("dialog", { name: "Eliminar aula" });
  await user.click(within(dialog).getByText("Eliminar aula", { selector: "button" }));
  expect(await screen.findByText("Aula Lab. 01 eliminada.")).toBeTruthy();
  expect(calls.filter((c) => c.method === "DELETE").map((c) => c.path)).toEqual(["/api/aulas/1", "/api/aulas/2"]);
});

test("Las pestañas navegan por URL y las pendientes lo indican", async () => {
  backend();
  const user = userEvent.setup();
  const { rerender } = renderSchedules({ tab: "horario" });
  expect(screen.getByText("Esta sección todavía está pendiente de implementación.")).toBeTruthy();
  await user.click(screen.getByText("Aulas", { selector: "button" }));
  expect(currentUrl()).toBe("/horarios/aulas");
  rerender(<Schedules canWrite tab="no-existe" />);
  await waitFor(() => expect(currentUrl()).toBe("/horarios/aulas"));
});
