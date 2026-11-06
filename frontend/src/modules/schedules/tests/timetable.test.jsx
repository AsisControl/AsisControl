import { beforeEach, describe, expect, test, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { fakeBackend, page, reply, signIn } from "../../../../tests/support/backend.js";
import { setUrl } from "../../../../tests/support/navigation.js";
import { Schedules } from "@/modules/schedules";

vi.mock("next/navigation", () => import("../../../../tests/support/navigation.js"));
vi.mock("next/link", () => import("../../../../tests/support/next-link.jsx"));

const section = (id, nombre, overrides = {}) => ({
  id,
  nombre,
  grado: 3,
  anioAcademico: 2026,
  capacidadMaxima: 30,
  docenteTutorId: null,
  docenteTutor: null,
  activo: true,
  version: 0,
  ...overrides,
});
const sections = [
  section(9, "B", { capacidadMaxima: 40 }),
  section(8, "A"),
  section(20, "C", { grado: 1, anioAcademico: 2024 }),
];
const assignment = (id, curso, seccionId, docenteId, docente) => ({
  id,
  cursoId: id * 100,
  curso,
  seccionId,
  seccion: "A",
  anioAcademico: 2026,
  grado: 3,
  docenteId,
  docente,
  activo: true,
  version: 0,
});
const assignments = [
  assignment(10, "Matemática", 8, 9, "Patricia Morales"),
  assignment(11, "Comunicación", 8, 7, "Luis Quispe"),
  assignment(12, "Arte", 9, 6, "Rosa Díaz"),
];
const classrooms = [
  { id: 1, codigo: "AULA-201", nombre: "Aula 201", tipoAula: "AULA_REGULAR", capacidad: 35, ubicacion: "", activo: true, version: 0 },
  { id: 2, codigo: "LAB-01", nombre: "Lab. 01", tipoAula: "LABORATORIO_COMPUTO", capacidad: 20, ubicacion: "", activo: true, version: 0 },
];
const block = (id, overrides = {}) => ({
  id,
  asignacionCursoId: 10,
  cursoId: 1000,
  curso: "Matemática",
  seccionId: 8,
  seccion: "A",
  docenteId: 9,
  docente: "Patricia Morales",
  aulaId: 1,
  aula: "Aula 201",
  diaSemana: "LUNES",
  horaInicio: "08:00:00",
  horaFin: "09:30:00",
  activo: true,
  version: 3,
  ...overrides,
});
const sectionBlocks = [
  block(1),
  block(2, { diaSemana: "MIERCOLES", horaInicio: "10:00:00", horaFin: "11:00:00" }),
  block(3, {
    asignacionCursoId: 11,
    curso: "Comunicación",
    docenteId: 7,
    docente: "Luis Quispe",
    aulaId: 2,
    aula: "Lab. 01",
    diaSemana: "MARTES",
  }),
];
/** Filtra como el servidor: todos los filtros con AND. */
const filtered = (all, query) =>
  all.filter(
    (item) =>
      (!query.seccionId || String(item.seccionId) === query.seccionId) &&
      (!query.docenteId || String(item.docenteId) === query.docenteId) &&
      (!query.aulaId || String(item.aulaId) === query.aulaId) &&
      (!query.cursoId || String(item.cursoId) === query.cursoId),
  );

function backend({ blocks = sectionBlocks, ...routes } = {}) {
  return fakeBackend({
    "GET /api/secciones": page(sections),
    "GET /api/asignaciones-cursos": page(assignments),
    "GET /api/aulas": page(classrooms),
    "GET /api/horarios": ({ query }) => page(filtered(blocks, query)),
    ...routes,
  });
}
const renderTimetable = (props = {}) => render(<Schedules canWrite tab="horario" {...props} />);
const grid = () => screen.findByRole("table", { name: "Horario semanal de la sección" });
const cellTexts = (table) =>
  within(table)
    .getAllByRole("row")
    .map((row) => [...row.children].map((cell) => cell.textContent.replace("Más acciones", "").trim()));
async function openNewBlock(user) {
  await grid();
  await user.click(screen.getByText("+ Nuevo bloque"));
  return screen.getByRole("dialog", { name: "Nuevo bloque" });
}
async function fillBlock(user, dialog, { course = "10", room = "1", day = "JUEVES", from = "08:00", to = "09:30" } = {}) {
  await user.selectOptions(within(dialog).getByLabelText(/^Asignación docente/), course);
  await user.selectOptions(within(dialog).getByLabelText(/^Aula/), room);
  await user.selectOptions(within(dialog).getByLabelText(/^Día/), day);
  fireEvent.change(within(dialog).getByLabelText(/^Hora de inicio/), { target: { value: from } });
  fireEvent.change(within(dialog).getByLabelText(/^Hora de fin/), { target: { value: to } });
}
async function chooseBlockAction(user, name, action) {
  await user.click(await screen.findByLabelText(`Más acciones para ${name}`));
  await user.click(screen.getByRole("menuitem", { name: action }));
}
const horarioCalls = (calls, filter) =>
  calls.filter((c) => c.method === "GET" && c.path === "/api/horarios" && filter(c.query));

beforeEach(() => {
  signIn({ permissions: ["ACADEMICO_LEER", "ACADEMICO_ESCRIBIR"] });
  setUrl("/horarios/horario");
});

test("Muestra la matriz semanal de la primera sección del año", async () => {
  const calls = backend();
  renderTimetable();
  const table = await grid();
  expect(cellTexts(table)[0]).toEqual(["Hora", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes"]);
  expect(within(table).getAllByRole("row")).toHaveLength(27);
  const atEight = within(table).getByRole("rowheader", { name: "08:00 – 08:30" }).closest("tr");
  expect(within(atEight).getByRole("button", { name: "Ver detalle de Matemática, Lunes 08:00" })).toBeTruthy();
  expect(within(atEight).getByRole("button", { name: "Ver detalle de Comunicación, Martes 08:00" })).toBeTruthy();
  expect(within(table).getByRole("rowheader", { name: "08:30 – 09:00" })).toBeTruthy();
  const card = within(atEight).getByRole("button", { name: "Ver detalle de Matemática, Lunes 08:00" }).closest("article");
  expect(card.style.height).toBe("186px");
  expect(card.textContent).toContain("08:00 – 09:30");
  expect(screen.getByText(/^3 bloques asignados · Última actualización: \d{2}:\d{2}/)).toBeTruthy();
  const year = screen.getByLabelText("Año académico");
  expect([...year.options].map((o) => o.textContent)).toEqual(["2026", "2024"]);
  expect(year.value).toBe("2026");
  const sectionSelect = screen.getByLabelText("Sección");
  expect([...sectionSelect.options].map((o) => o.textContent)).toEqual(["3° A · aforo 30", "3° B · aforo 40"]);
  expect(horarioCalls(calls, () => true)[0].query).toEqual({
    anioAcademico: "2026",
    seccionId: "8",
    page: "0",
    size: "100",
  });
  expect(calls.find((c) => c.path === "/api/secciones").query).toMatchObject({ activo: "true" });
  expect(calls.find((c) => c.path === "/api/aulas").query).not.toHaveProperty("estado");
});

test("Recorre todas las páginas y agrega el sábado si tiene bloques", async () => {
  const calls = backend({
    "GET /api/horarios": ({ query }) =>
      query.page === "0"
        ? page([block(1)], 2)
        : { ...page([block(4, { diaSemana: "SABADO", horaInicio: "07:00:00", horaFin: "08:00:00" })], 2), page: 1 },
  });
  renderTimetable();
  const table = await grid();
  expect(within(table).getByRole("columnheader", { name: "Sábado" })).toBeTruthy();
  expect(within(table).getByRole("rowheader", { name: "07:00 – 07:30" })).toBeTruthy();
  expect(within(table).getByRole("button", { name: "Ver detalle de Matemática, Sábado 07:00" })).toBeTruthy();
  expect(horarioCalls(calls, () => true).map((c) => c.query.page)).toEqual(["0", "1"]);
});

test("Cambiar de sección o de año vuelve a consultar el horario", async () => {
  const calls = backend();
  const user = userEvent.setup();
  renderTimetable();
  await grid();
  await user.selectOptions(screen.getByLabelText("Sección"), "9");
  expect(await screen.findByText("La sección 3° B aún no tiene bloques de horario. Usa «Nuevo bloque» para asignar el primero.")).toBeTruthy();
  await user.selectOptions(screen.getByLabelText("Año académico"), "2024");
  expect(await screen.findByText(/La sección 1° C aún no tiene bloques/)).toBeTruthy();
  expect(horarioCalls(calls, () => true).map((c) => [c.query.anioAcademico, c.query.seccionId])).toEqual([
    ["2026", "8"],
    ["2026", "9"],
    ["2024", "20"],
  ]);
  await user.click(screen.getByText("Actualizar"));
  await waitFor(() => expect(horarioCalls(calls, () => true)).toHaveLength(4));
});

test("Sin permiso de escritura la matriz es de solo lectura", async () => {
  const calls = backend();
  renderTimetable({ canWrite: false });
  await grid();
  expect(screen.queryByText("+ Nuevo bloque")).toBeNull();
  expect(screen.queryByLabelText(/Más acciones/)).toBeNull();
  expect(calls.some((c) => c.path === "/api/asignaciones-cursos" || c.path === "/api/aulas")).toBe(false);
});

test("Informa si no hay secciones o si falla la carga", async () => {
  backend({ "GET /api/secciones": page([]) });
  const { unmount } = renderTimetable();
  expect(await screen.findByText(/No hay secciones habilitadas/)).toBeTruthy();
  unmount();
  backend({ "GET /api/secciones": reply(403, null) });
  const second = renderTimetable();
  expect((await screen.findByRole("alert")).textContent).toBe("No tienes permiso para realizar esta operación.");
  second.unmount();
  backend({ "GET /api/horarios": reply(500, { message: "Error del servidor" }) });
  renderTimetable();
  expect((await screen.findByRole("alert")).textContent).toBe("Error del servidor");
  expect(screen.queryByText(/aún no tiene bloques/)).toBeNull();
});

test("Valida los campos obligatorios antes de enviar", async () => {
  const calls = backend();
  const user = userEvent.setup();
  renderTimetable();
  const dialog = await openNewBlock(user);
  expect(within(dialog).getByText("Sección 3° A · 2026 · aforo máximo 30")).toBeTruthy();
  expect([...within(dialog).getByLabelText(/^Asignación docente/).options].map((o) => o.textContent)).toEqual([
    "Seleccionar curso y docente",
    "Matemática · Patricia Morales",
    "Comunicación · Luis Quispe",
  ]);
  await user.click(within(dialog).getByText("Confirmar bloque"));
  expect(within(dialog).getByText("Seleccione la asignación docente.")).toBeTruthy();
  expect(within(dialog).getByText("Seleccione el aula.")).toBeTruthy();
  expect(within(dialog).getByText("Indique la hora de inicio.")).toBeTruthy();
  expect(within(dialog).getByText("Indique la hora de fin.")).toBeTruthy();
  const starts = [...within(dialog).getByLabelText(/^Hora de inicio/).options].map((o) => o.value);
  const ends = [...within(dialog).getByLabelText(/^Hora de fin/).options].map((o) => o.value);
  expect(starts.slice(0, 4)).toEqual(["", "07:00", "07:30", "08:00"]);
  expect(starts.at(-1)).toBe("19:30");
  expect(ends[1]).toBe("07:30");
  expect(ends.at(-1)).toBe("20:00");
  expect(starts).not.toContain("08:15");
  fireEvent.change(within(dialog).getByLabelText(/^Hora de inicio/), { target: { value: "10:00" } });
  fireEvent.change(within(dialog).getByLabelText(/^Hora de fin/), { target: { value: "09:00" } });
  await user.click(within(dialog).getByText("Confirmar bloque"));
  expect(within(dialog).getByText("Debe ser posterior a la hora de inicio.")).toBeTruthy();
  expect(calls.some((c) => c.method === "POST")).toBe(false);
});

test("Crea un bloque sin conflictos y recarga la matriz", async () => {
  const calls = backend({
    "POST /api/horarios": ({ body }) => reply(201, block(9, { ...body, diaSemana: body.diaSemana, horaInicio: `${body.horaInicio}:00`, horaFin: `${body.horaFin}:00` })),
  });
  const user = userEvent.setup();
  renderTimetable();
  const dialog = await openNewBlock(user);
  await fillBlock(user, dialog);
  expect(within(dialog).getByText("Verificando cruces de docente y aula…")).toBeTruthy();
  expect(await within(dialog).findByText("Sin conflictos de docente, aula, sección ni aforo.")).toBeTruthy();
  expect(horarioCalls(calls, (q) => q.docenteId === "9" || q.aulaId === "1").map((c) => c.query)).toEqual(
    expect.arrayContaining([
      expect.objectContaining({ anioAcademico: "2026", docenteId: "9" }),
      expect.objectContaining({ anioAcademico: "2026", aulaId: "1" }),
    ]),
  );
  await user.click(within(dialog).getByText("Confirmar bloque"));
  expect(await screen.findByText("Bloque de Matemática registrado: Jueves 08:00 – 09:30.")).toBeTruthy();
  expect(calls.find((c) => c.method === "POST").body).toEqual({
    asignacionCursoId: 10,
    aulaId: 1,
    diaSemana: "JUEVES",
    horaInicio: "08:00",
    horaFin: "09:30",
  });
  await waitFor(() => expect(horarioCalls(calls, (q) => q.seccionId === "8")).toHaveLength(2));
});

test("Recalcula los cruces de docente, aula, sección y aforo antes de guardar", async () => {
  const other = block(50, {
    asignacionCursoId: 60,
    curso: "Ciencia",
    seccionId: 9,
    seccion: "B",
    aulaId: 2,
    aula: "Lab. 01",
    diaSemana: "JUEVES",
    horaInicio: "08:30:00",
  });
  const busyRoom = block(51, {
    asignacionCursoId: 61,
    curso: "Arte",
    seccionId: 9,
    seccion: "B",
    docenteId: 6,
    docente: "Rosa Díaz",
    diaSemana: "VIERNES",
    horaInicio: "08:00:00",
  });
  const calls = backend({ blocks: [...sectionBlocks, other, busyRoom] });
  const user = userEvent.setup();
  renderTimetable();
  const dialog = await openNewBlock(user);
  await fillBlock(user, dialog);
  const teacher = await within(dialog).findByRole("alert", { name: "Conflictos detectados" });
  expect(teacher.textContent).toContain("Cruce de docente: Patricia Morales ya dicta Ciencia · Sección B · Jueves 08:30 – 09:30.");
  await user.click(within(dialog).getByText("Confirmar bloque"));
  expect(within(dialog).getByText("Resuelve los conflictos antes de confirmar el bloque.")).toBeTruthy();
  expect(calls.some((c) => c.method === "POST")).toBe(false);
  await user.selectOptions(within(dialog).getByLabelText(/^Día/), "VIERNES");
  expect((await within(dialog).findByText(/^Aula ocupada:/)).parentElement.textContent).toBe(
    "Aula ocupada: Aula 201 ya está ocupada: Arte · Sección B · Viernes 08:00 – 09:30.",
  );
  await user.selectOptions(within(dialog).getByLabelText(/^Aula/), "2");
  await user.selectOptions(within(dialog).getByLabelText(/^Asignación docente/), "11");
  fireEvent.change(within(dialog).getByLabelText(/^Hora de inicio/), { target: { value: "07:00" } });
  fireEvent.change(within(dialog).getByLabelText(/^Hora de fin/), { target: { value: "07:30" } });
  const capacity = await within(dialog).findByText(/^Aforo insuficiente:/);
  expect(capacity.parentElement.textContent).toBe(
    "Aforo insuficiente: El aula admite 20 estudiantes y la sección tiene una capacidad máxima de 30.",
  );
  await user.selectOptions(within(dialog).getByLabelText(/^Día/), "MIERCOLES");
  fireEvent.change(within(dialog).getByLabelText(/^Hora de inicio/), { target: { value: "10:30" } });
  fireEvent.change(within(dialog).getByLabelText(/^Hora de fin/), { target: { value: "11:30" } });
  expect((await within(dialog).findByText(/^Cruce de sección:/)).parentElement.textContent).toBe(
    "Cruce de sección: La sección ya tiene Matemática · Sección A · Miércoles 10:00 – 11:00.",
  );
  await user.selectOptions(within(dialog).getByLabelText(/^Aula/), "1");
  fireEvent.change(within(dialog).getByLabelText(/^Hora de inicio/), { target: { value: "11:00" } });
  expect(await within(dialog).findByText("Sin conflictos de docente, aula, sección ni aforo.")).toBeTruthy();
});

test("Muestra el conflicto que devuelve el servidor y recarga la matriz", async () => {
  let attempts = 0;
  const calls = backend({
    "POST /api/horarios": () => {
      attempts++;
      return attempts === 1
        ? reply(409, { code: "HORARIO_AULA_OCUPADA", message: "El aula ya esta ocupada en ese horario" })
        : reply(422, { code: "AULA_CAPACIDAD_INSUFICIENTE", message: "El aula no tiene capacidad para la seccion asignada" });
    },
  });
  const user = userEvent.setup();
  renderTimetable();
  const dialog = await openNewBlock(user);
  await fillBlock(user, dialog);
  await within(dialog).findByText("Sin conflictos de docente, aula, sección ni aforo.");
  await user.click(within(dialog).getByText("Confirmar bloque"));
  const rejected = await within(dialog).findByRole("alert", { name: "El servidor rechazó el bloque" });
  expect(rejected.textContent).toBe("El servidor rechazó el bloqueAula ocupada: El aula ya esta ocupada en ese horario");
  await waitFor(() => expect(horarioCalls(calls, (q) => q.seccionId === "8")).toHaveLength(2));
  await user.click(within(dialog).getByText("Confirmar bloque"));
  expect(await within(dialog).findByText(/^Aforo insuficiente:/)).toBeTruthy();
  expect(horarioCalls(calls, (q) => q.seccionId === "8")).toHaveLength(2);
  await user.selectOptions(within(dialog).getByLabelText(/^Día/), "VIERNES");
  expect(within(dialog).queryByRole("alert", { name: "El servidor rechazó el bloque" })).toBeNull();
});

test("Los errores sin código de negocio se muestran como aviso general", async () => {
  backend({ "POST /api/horarios": reply(403, null) });
  const user = userEvent.setup();
  renderTimetable();
  const dialog = await openNewBlock(user);
  await fillBlock(user, dialog);
  await within(dialog).findByText("Sin conflictos de docente, aula, sección ni aforo.");
  await user.click(within(dialog).getByText("Confirmar bloque"));
  expect(await within(dialog).findByText("No tienes permiso para realizar esta operación.")).toBeTruthy();
  expect(within(dialog).queryByRole("alert", { name: "El servidor rechazó el bloque" })).toBeNull();
});

test("Si no se pueden consultar los cruces, el servidor valida al confirmar", async () => {
  const calls = backend({
    "GET /api/horarios": ({ query }) =>
      query.seccionId ? page(filtered(sectionBlocks, query)) : reply(500, { message: "Error" }),
    "POST /api/horarios": reply(201, block(9, { diaSemana: "JUEVES" })),
  });
  const user = userEvent.setup();
  renderTimetable();
  const dialog = await openNewBlock(user);
  await fillBlock(user, dialog);
  expect(await within(dialog).findByText(/No se pudieron consultar los horarios del docente y del aula/)).toBeTruthy();
  await user.click(within(dialog).getByText("Confirmar bloque"));
  expect(await screen.findByText("Bloque de Matemática registrado: Jueves 08:00 – 09:30.")).toBeTruthy();
  expect(calls.some((c) => c.method === "POST")).toBe(true);
});

test("Edita un bloque enviando su versión y avisa si otro usuario lo cambió", async () => {
  let stale = true;
  const calls = backend({
    "PUT /api/horarios/:id": ({ body }) =>
      stale
        ? reply(409, { code: "VERSION_DESACTUALIZADA", message: "El horario fue modificado; vuelva a cargarlo" })
        : block(1, { ...body, horaInicio: "08:00:00", horaFin: "10:00:00", version: 4 }),
  });
  const user = userEvent.setup();
  renderTimetable();
  await grid();
  await chooseBlockAction(user, "Matemática Lunes 08:00", "Editar");
  const dialog = screen.getByRole("dialog", { name: "Editar bloque" });
  expect(within(dialog).getByLabelText(/^Asignación docente/).value).toBe("10");
  expect(within(dialog).getByLabelText(/^Aula/).value).toBe("1");
  expect(within(dialog).getByLabelText(/^Día/).value).toBe("LUNES");
  expect(within(dialog).getByLabelText(/^Hora de inicio/).value).toBe("08:00");
  fireEvent.change(within(dialog).getByLabelText(/^Hora de fin/), { target: { value: "10:00" } });
  // El propio bloque no cuenta como conflicto.
  expect(await within(dialog).findByText("Sin conflictos de docente, aula, sección ni aforo.")).toBeTruthy();
  await user.click(within(dialog).getByText("Guardar cambios"));
  expect(await within(dialog).findByText(/^Bloque modificado por otro usuario:/)).toBeTruthy();
  await waitFor(() => expect(horarioCalls(calls, (q) => q.seccionId === "8")).toHaveLength(2));
  stale = false;
  await user.click(within(dialog).getByText("Guardar cambios"));
  expect(await screen.findByText("Bloque de Matemática actualizado: Lunes 08:00 – 10:00.")).toBeTruthy();
  expect(calls.filter((c) => c.method === "PUT").at(-1)).toMatchObject({
    path: "/api/horarios/1",
    body: { asignacionCursoId: 10, aulaId: 1, diaSemana: "LUNES", horaInicio: "08:00", horaFin: "10:00", version: 3 },
  });
});

test("No permite guardar un bloque cuyo aula o asignación ya no está disponible", async () => {
  const orphan = block(7, { asignacionCursoId: 99, curso: "Música", docente: "Ana Ruiz", aulaId: 5, aula: "Aula 500", diaSemana: "VIERNES" });
  const calls = backend({ blocks: [orphan] });
  const user = userEvent.setup();
  renderTimetable();
  await grid();
  await chooseBlockAction(user, "Música Viernes 08:00", "Editar");
  const dialog = screen.getByRole("dialog", { name: "Editar bloque" });
  expect(within(dialog).getByRole("option", { name: "Aula 500 (inhabilitada)" })).toBeTruthy();
  expect(within(dialog).getByRole("option", { name: "Música · Ana Ruiz (no disponible)" })).toBeTruthy();
  const list = within(dialog).getByRole("alert", { name: "Conflictos detectados" });
  expect(list.textContent).toContain("Aula 500 está inhabilitada; elige otra aula.");
  expect(list.textContent).toContain("La asignación de Música con Ana Ruiz ya no está activa.");
  await user.click(within(dialog).getByText("Guardar cambios"));
  expect(within(dialog).getByText("Resuelve los conflictos antes de confirmar el bloque.")).toBeTruthy();
  expect(calls.some((c) => c.method === "PUT")).toBe(false);
  await user.click(within(dialog).getByText("Cancelar"));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("Elimina un bloque y muestra el rechazo del servidor", async () => {
  let missing = true;
  const calls = backend({
    "DELETE /api/horarios/:id": () =>
      missing ? reply(404, { code: "HORARIO_NO_ENCONTRADO", message: "No existe un horario activo con id 3" }) : reply(204),
  });
  const user = userEvent.setup();
  renderTimetable();
  await grid();
  await chooseBlockAction(user, "Comunicación Martes 08:00", "Eliminar");
  const dialog = screen.getByRole("dialog", { name: "Eliminar bloque" });
  expect(within(dialog).getByText("Comunicación · Martes 08:00 – 09:30")).toBeTruthy();
  await user.click(within(dialog).getByText("Eliminar bloque", { selector: "button" }));
  expect(await within(dialog).findByText("No existe un horario activo con id 3")).toBeTruthy();
  missing = false;
  await user.click(within(dialog).getByText("Eliminar bloque", { selector: "button" }));
  expect(await screen.findByText("Bloque de Comunicación eliminado.")).toBeTruthy();
  expect(calls.filter((c) => c.method === "DELETE").map((c) => c.path)).toEqual(["/api/horarios/3", "/api/horarios/3"]);
});

test("Avisa si la sección no tiene asignaciones y enlaza a la pestaña donde se registran", async () => {
  backend({ "GET /api/asignaciones-cursos": page([]) });
  const user = userEvent.setup();
  renderTimetable();
  const dialog = await openNewBlock(user);
  expect(await within(dialog).findByText(/La sección 3° A · 2026 no tiene asignaciones docentes activas/)).toBeTruthy();
  expect(within(dialog).getByRole("link", { name: "Asignaciones docentes" }).getAttribute("href"))
    .toBe("/horarios/asignaciones-docentes");
});

test("Al abrir Nuevo bloque muestra asignaciones registradas después de entrar a la pestaña", async () => {
  let current = [];
  const calls = backend({ "GET /api/asignaciones-cursos": () => page(current) });
  const user = userEvent.setup();
  renderTimetable();
  await grid();
  current = assignments;
  const dialog = await openNewBlock(user);
  await within(dialog).findByRole("option", { name: "Matemática · Patricia Morales" });
  expect(within(dialog).queryByText(/no tiene asignaciones docentes activas/)).toBeNull();
  const listings = calls.filter((c) => c.path === "/api/asignaciones-cursos");
  expect(listings).toHaveLength(2);
  expect(listings.at(-1).query.estado).toBe("ACTIVAS");
});

test("Si no se pueden actualizar las asignaciones lo indica sin afirmar que no existen", async () => {
  let fail = false;
  backend({ "GET /api/asignaciones-cursos": () => (fail ? reply(500, { message: "Error" }) : page([])) });
  const user = userEvent.setup();
  renderTimetable();
  await grid();
  fail = true;
  const dialog = await openNewBlock(user);
  expect(await within(dialog).findByText(/No se pudieron actualizar las asignaciones docentes/)).toBeTruthy();
  expect(within(dialog).queryByText(/no tiene asignaciones docentes activas/)).toBeNull();
});

describe("Perspectivas de la matriz (US-24)", () => {
  const otherSection = block(4, {
    asignacionCursoId: 12,
    seccionId: 9,
    seccion: "B",
    diaSemana: "VIERNES",
  });
  const allBlocks = [...sectionBlocks, otherSection];
  const view = async (user, perspective) => {
    await grid();
    await user.selectOptions(screen.getByLabelText("Ver horario por"), perspective);
  };

  test.each([
    ["docente", "Docente", "Patricia Morales", "docenteId", "9"],
    ["aula", "Aula", "AULA-201 · Aula 201", "aulaId", "1"],
    ["curso", "Curso", "Matemática · 3° grado", "cursoId", "1000"],
  ])("Por %s consulta solo sus bloques e indica la sección", async (perspective, label, option, param, value) => {
    const calls = backend({
      blocks: allBlocks.map((item) => (item.curso === "Comunicación" ? { ...item, cursoId: 1100 } : item)),
      "GET /api/asignaciones-cursos": page([
        { ...assignment(10, "Matemática", 8, 9, "Patricia Morales"), cursoId: 1000 },
        assignment(11, "Comunicación", 8, 7, "Luis Quispe"),
      ]),
    });
    const user = userEvent.setup();
    renderTimetable();
    await view(user, perspective);
    const select = await screen.findByLabelText(label);
    await within(select).findByRole("option", { name: option });
    await user.selectOptions(select, value);
    const table = await screen.findByRole("table", { name: `Horario semanal por ${label.toLowerCase()}` });
    await within(table).findByText("3° B");
    expect(horarioCalls(calls, (q) => q[param] === value).at(-1).query.anioAcademico).toBe("2026");
    expect(within(table).getAllByText("3° A").length).toBeGreaterThan(0);
    expect(within(table).queryByText("Comunicación")).toBeNull();
    expect(screen.getByRole("heading", { name: `Horario por ${label.toLowerCase()}` })).toBeTruthy();
  });

  test("Las opciones salen de las asignaciones del año y cambiar de opción reconsulta", async () => {
    const calls = backend({
      blocks: allBlocks,
      "GET /api/asignaciones-cursos": page([
        ...assignments,
        { ...assignment(13, "Música", 20, 5, "Ana Antigua"), anioAcademico: 2024 },
      ]),
    });
    const user = userEvent.setup();
    renderTimetable();
    await view(user, "docente");
    const select = await screen.findByLabelText("Docente");
    await waitFor(() =>
      expect([...select.options].map((o) => o.textContent)).toEqual([
        "Luis Quispe",
        "Patricia Morales",
        "Rosa Díaz",
      ]),
    );
    await user.selectOptions(select, "9");
    await screen.findAllByText("Matemática");
    expect(horarioCalls(calls, (q) => q.docenteId === "9")).not.toHaveLength(0);
  });

  test("Clic en un bloque abre el detalle completo en cualquier perspectiva", async () => {
    backend({ blocks: allBlocks });
    const user = userEvent.setup();
    renderTimetable();
    await view(user, "aula");
    await user.click(await screen.findByRole("button", { name: "Ver detalle de Matemática, Viernes 08:00" }));
    const dialog = screen.getByRole("dialog", { name: "Detalle del bloque" });
    const pairs = [...dialog.querySelectorAll("dl > div")].map((row) => [
      row.querySelector("dt").textContent,
      row.querySelector("dd").textContent,
    ]);
    expect(pairs).toEqual([
      ["Curso", "Matemática"],
      ["Sección", "3° B · 2026"],
      ["Docente responsable", "Patricia Morales"],
      ["Aula", "Aula 201"],
      ["Día", "Viernes"],
      ["Hora de inicio", "08:00"],
      ["Hora de fin", "09:30"],
    ]);
    await user.click(within(dialog).getByRole("button", { name: "Cerrar" }));
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  test("El detalle también abre en la vista por sección sin perder el menú de edición", async () => {
    backend();
    const user = userEvent.setup();
    renderTimetable();
    await grid();
    expect(screen.getAllByLabelText(/Más acciones/).length).toBeGreaterThan(0);
    const blockCard = screen.getByRole("button", { name: "Ver detalle de Comunicación, Martes 08:00" }).closest("article");
    fireEvent.click(blockCard);
    const dialog = screen.getByRole("dialog", { name: "Detalle del bloque" });
    expect(within(dialog).getByText("3° A · 2026")).toBeTruthy();
    expect(within(dialog).getByText("Luis Quispe")).toBeTruthy();
  });

  test("El menú de acciones no abre el detalle del bloque", async () => {
    backend();
    renderTimetable();
    await grid();
    await userEvent.setup().click(screen.getByLabelText("Más acciones para Matemática Lunes 08:00"));
    expect(screen.queryByRole("dialog", { name: "Detalle del bloque" })).toBeNull();
    expect(screen.getByRole("menuitem", { name: "Editar" })).toBeTruthy();
  });

  test("Fuera de la vista por sección es de consulta: sin crear ni editar", async () => {
    backend({ blocks: allBlocks });
    const user = userEvent.setup();
    renderTimetable();
    expect(await screen.findByText("+ Nuevo bloque")).toBeTruthy();
    await view(user, "docente");
    await screen.findByRole("table", { name: "Horario semanal por docente" });
    expect(screen.queryByText("+ Nuevo bloque")).toBeNull();
    expect(screen.queryByLabelText(/Más acciones/)).toBeNull();
    await user.selectOptions(screen.getByLabelText("Ver horario por"), "seccion");
    await grid();
    expect(screen.getByText("+ Nuevo bloque")).toBeTruthy();
  });

  test("Sin bloques muestra la matriz vacía y un mensaje claro", async () => {
    backend({ blocks: [] });
    const user = userEvent.setup();
    renderTimetable();
    let table = await grid();
    expect(cellTexts(table)[0]).toEqual(["Hora", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes"]);
    expect(within(table).getByText(/La sección 3° A aún no tiene bloques/)).toBeTruthy();
    await user.selectOptions(screen.getByLabelText("Ver horario por"), "docente");
    table = await screen.findByRole("table", { name: "Horario semanal por docente" });
    expect(within(table).getByText("Luis Quispe no tiene bloques de horario en 2026.")).toBeTruthy();
  });

  test("Sin opciones lo explica y no consulta el horario", async () => {
    const calls = backend({ "GET /api/asignaciones-cursos": page([]) });
    const user = userEvent.setup();
    renderTimetable();
    await view(user, "curso");
    expect(await screen.findByText("No hay cursos con asignaciones activas en 2026.")).toBeTruthy();
    expect(screen.getByLabelText("Curso").disabled).toBe(true);
    expect(horarioCalls(calls, (q) => q.cursoId)).toHaveLength(0);
  });

  test("Con solo lectura también consulta las perspectivas; si fallan, lo informa", async () => {
    let fail = false;
    backend({
      blocks: allBlocks,
      "GET /api/aulas": () => (fail ? reply(500, { message: "Error de aulas" }) : page(classrooms)),
    });
    const user = userEvent.setup();
    const first = renderTimetable({ canWrite: false });
    await view(user, "docente");
    expect(await screen.findByRole("table", { name: "Horario semanal por docente" })).toBeTruthy();
    expect(screen.queryByLabelText(/Más acciones/)).toBeNull();
    first.unmount();
    fail = true;
    renderTimetable({ canWrite: false });
    await view(user, "aula");
    expect((await screen.findByRole("alert")).textContent).toBe("Error de aulas");
  });
});
